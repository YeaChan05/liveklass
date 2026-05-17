package org.yechan.enrollment

import org.springframework.scheduling.annotation.Scheduled
import org.yechan.course.CourseRepository
import org.yechan.course.CourseStatus

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
    private val waitlistRepository: EnrollmentWaitlistRepository,
    private val courseRepository: CourseRepository,
    private val waitlistCoordinator: EnrollmentWaitlistCoordinator,
) : WaitlistPromotionRecoveryUseCase {
    override fun recoverPromotions() {
        val courseIds = waitlistRepository.findCourseIds().ifEmpty { return }

        val courses = courseRepository.findAllOpenedCoursesByIds(courseIds)
        courses.forEach { course ->
            val courseId = course.courseId ?: return@forEach
            val promotableCount = course.seatLeftCount

            if (course.status != CourseStatus.OPEN || promotableCount <= 0) {
                return@forEach
            }

            waitlistCoordinator.promoteAfterSeatRelease(courseId, promotableCount)
        }
    }
}
