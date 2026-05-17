package org.yechan.enrollment

import org.springframework.scheduling.annotation.Scheduled
import org.yechan.course.CourseReader

interface WaitlistPromotionRecoveryUseCase {
    fun recoverPromotions()
}

open class EnrollmentWaitlistScheduler(
    private val waitlistPromotionRecoveryService: WaitlistPromotionRecoveryUseCase,
) {
    @Scheduled(fixedDelay = 5_000)
    fun processWaitlists() {
        waitlistPromotionRecoveryService.recoverPromotions()
    }
}

class WaitlistPromotionRecoveryService(
    private val waitlistReader: EnrollmentWaitlistReader,
    private val courseReader: CourseReader,
    private val waitlistWriter: EnrollmentWaitlistWriter,
) : WaitlistPromotionRecoveryUseCase {
    override fun recoverPromotions() {
        val courseIds = waitlistReader.findCourseIds().ifEmpty { return }

        val courses = courseReader.getOpenedCoursesByIds(courseIds)
        courses.forEach { course ->
            val courseId = course.courseId
            val promotableCount = course.seatLeftCount

            if (promotableCount <= 0) {
                return@forEach
            }

            waitlistWriter.promoteAfterSeatRelease(courseId, promotableCount)
        }
    }
}
