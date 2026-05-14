package org.yechan.enrollment

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

        val promotions = courses.flatMap { course ->
            val courseId = course.courseId ?: return@flatMap emptyList()
            val promotableCount = course.seatLeftCount
            val now = LocalDateTime.now()

            if (course.status != CourseStatus.OPEN || promotableCount <= 0) {
                return@flatMap emptyList()
            }

            (1..promotableCount).mapNotNull {
                val waitlist = waitlistRepository.pop(courseId) ?: return@mapNotNull null

                EnrollmentModelData(
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

        courseBulkWriter.reserveSeatsBulk(reservedCountsByCourseId)

        enrollmentBulkWriter.saveAllBulk(promotions)
    }
}
