package org.yechan.enrollment

import org.springframework.scheduling.annotation.Scheduled
import org.yechan.course.CourseRepository
import org.yechan.course.CourseStatus
import java.time.LocalDateTime

open class EnrollmentWaitlistScheduler(
    private val waitlistRepository: EnrollmentWaitlistRepository,
    private val courseRepository: CourseRepository,
    private val enrollmentWaitlistProcessor: EnrollmentWaitlistProcessor,
) {
    @Scheduled(fixedDelay = 5_000)
    fun processWaitlists() {
        val courseIds = waitlistRepository.findCourseIds().ifEmpty { return }

        val courses = courseRepository.findAllOpenedCoursesByIds(courseIds)
        val poppedWaitlists = mutableListOf<EnrollmentWaitlistEntry>()

        val candidates = courses.flatMap { course ->
            val courseId = course.courseId ?: return@flatMap emptyList()
            val promotableCount = course.seatLeftCount
            val now = LocalDateTime.now()

            if (course.status != CourseStatus.OPEN || promotableCount <= 0) {
                return@flatMap emptyList()
            }

            (1..promotableCount).mapNotNull {
                val waitlist = waitlistRepository.pop(courseId) ?: return@mapNotNull null
                poppedWaitlists += waitlist
                EnrollmentWaitlistPromotionCandidate(
                    waitlist = waitlist,
                    promotedAt = now,
                )
            }
        }
            .distinctBy { it.waitlist.courseId to it.waitlist.memberId }
            .ifEmpty { return }

        try {
            enrollmentWaitlistProcessor.promote(candidates)
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

data class EnrollmentWaitlistPromotionCandidate(
    val waitlist: EnrollmentWaitlistEntry,
    val promotedAt: LocalDateTime,
)
