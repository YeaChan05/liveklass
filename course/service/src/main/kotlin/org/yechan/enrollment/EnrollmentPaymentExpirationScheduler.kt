package org.yechan.enrollment

import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.scheduling.annotation.Scheduled
import java.time.Clock
import java.time.LocalDateTime

private val log = KotlinLogging.logger {}

interface EnrollmentPaymentExpirationUseCase {
    fun expirePaymentPendingEnrollments()
}

open class EnrollmentPaymentExpirationScheduler(
    private val enrollmentPaymentExpirationService: EnrollmentPaymentExpirationUseCase,
) {
    @Scheduled(fixedDelayString = $$"${enrollment.payment-expiration.fixed-delay-ms:60000}")
    fun expirePaymentPendingEnrollments() {
        enrollmentPaymentExpirationService.expirePaymentPendingEnrollments()
    }
}

open class EnrollmentPaymentExpirationService(
    private val enrollmentRepository: EnrollmentRepository,
    private val enrollmentExpirationProcessor: EnrollmentExpirationProcessor,
    private val waitlistCoordinator: EnrollmentWaitlistCoordinator,
    private val clock: Clock,
) : EnrollmentPaymentExpirationUseCase {
    override fun expirePaymentPendingEnrollments() {
        val now = LocalDateTime.now(clock)
        val targets =
            enrollmentRepository.findExpiredPaymentPendingTargets(now = now, limit = 100)

        if (targets.isEmpty()) {
            return
        }

        val countsByCourseId =
            enrollmentExpirationProcessor.expireAll(
                courseIds = targets.map { it.courseId }.distinct(),
                now = now,
            )

        val expiredCount = countsByCourseId.values.sum()
        countsByCourseId.forEach { (courseId, expiredCount) ->
            waitlistCoordinator.promoteAfterSeatRelease(courseId, expiredCount)
        }

        if (expiredCount > 0) {
            log.info {
                "결제 대기 만료 처리 완료: ${expiredCount}건, ${countsByCourseId.size}개 강의의 반환 좌석을 처리했습니다."
            }
        }
    }
}
