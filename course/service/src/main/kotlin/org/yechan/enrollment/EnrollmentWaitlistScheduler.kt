package org.yechan.enrollment

import io.hypersistence.tsid.TSID
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.transaction.annotation.Transactional
import org.yechan.course.CourseRepository
import org.yechan.course.CourseStatus
import java.time.Duration
import java.time.LocalDateTime

open class EnrollmentWaitlistScheduler(
    private val waitlistRepository: EnrollmentWaitlistRepository,
    private val courseRepository: CourseRepository,
    private val courseBulkWriter: CourseBulkWriter,
    private val enrollmentBulkWriter: EnrollmentBulkWriter,
    private val paymentPendingExpiresIn: Duration = Duration.ofMinutes(10),
) {
    @Scheduled(fixedDelay = 5_000)
    @Transactional
    open fun processWaitlists() {
        val courseIds = waitlistRepository.findCourseIds().ifEmpty { return }

        val courses = courseRepository.findAllOpenedCoursesByIds(courseIds)
        val poppedWaitlists = mutableListOf<EnrollmentWaitlistEntry>()

        val promotions = courses.flatMap { course ->
            val courseId = course.courseId ?: return@flatMap emptyList()
            val promotableCount = course.seatLeftCount
            val now = LocalDateTime.now()

            if (course.status != CourseStatus.OPEN || promotableCount <= 0) {
                return@flatMap emptyList()
            }

            (1..promotableCount).mapNotNull {
                val waitlist = waitlistRepository.pop(courseId) ?: return@mapNotNull null
                poppedWaitlists += waitlist

                EnrollmentModelData(
                    enrollmentId = TSID.Factory.getTsid().toLong(),
                    courseId = courseId,
                    memberId = waitlist.memberId,
                    paymentPendingStartedAt = now,
                    paymentPendingExpiresAt = now.plus(paymentPendingExpiresIn),
                )
            }
        }.ifEmpty { return }

        val reservedCountsByCourseId = promotions
            .groupingBy { it.courseId }
            .eachCount()

        try {
            courseBulkWriter.reserveSeatsBulk(reservedCountsByCourseId)
            enrollmentBulkWriter.saveAllBulk(promotions)
        } catch (e: RuntimeException) {
            restoreWaitlists(poppedWaitlists)
            throw e
        }
    }

    private fun restoreWaitlists(waitlists: List<EnrollmentWaitlistEntry>) {
        waitlists.forEach { waitlist ->
            waitlistRepository.enqueue(
                courseId = waitlist.courseId,
                memberId = waitlist.memberId,
                requestedAt = waitlist.requestedAt,
            )
            waitlistRepository.markSoldOut(waitlist.courseId)
        }
    }
}
