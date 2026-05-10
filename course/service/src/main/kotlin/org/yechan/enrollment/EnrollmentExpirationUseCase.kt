package org.yechan.enrollment

import org.springframework.transaction.annotation.Transactional
import org.yechan.course.CourseInvalidStateException
import org.yechan.course.CourseRepository
import java.time.LocalDateTime

interface EnrollmentExpirationUseCase {
    fun expirePaymentPendingEnrollments(
        now: LocalDateTime = LocalDateTime.now(),
    ): Int
}

@Transactional(readOnly = true)
class EnrollmentExpirationService(
    private val enrollmentRepository: EnrollmentRepository,
    private val courseRepository: CourseRepository,
    private val waitlistRepository: EnrollmentWaitlistRepository,
) : EnrollmentExpirationUseCase {
    @Transactional
    override fun expirePaymentPendingEnrollments(now: LocalDateTime): Int {
        val targets =
            enrollmentRepository.findExpiredPaymentPendingTargets(
                now = now,
                limit = 100,
            )

        var expiredCount = 0

        targets.forEach { target ->
            val expired =
                enrollmentRepository.expirePaymentPendingIfExpired(
                    enrollmentId = target.enrollmentId,
                    now = now,
                )

            if (!expired) {
                return@forEach
            }

            val released =
                courseRepository.releaseSeatIfPossible(target.courseId)

            if (!released) {
                throw CourseInvalidStateException("만료된 수강 신청의 좌석을 반환할 수 없습니다.")
            }

            waitlistRepository.clearSoldOut(target.courseId)

            expiredCount++
        }

        return expiredCount
    }
}
