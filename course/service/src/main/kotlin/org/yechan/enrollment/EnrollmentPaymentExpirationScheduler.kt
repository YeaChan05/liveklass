package org.yechan.enrollment

import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.scheduling.annotation.Scheduled
import java.time.Clock
import java.time.LocalDateTime

private val log = KotlinLogging.logger {}

open class EnrollmentPaymentExpirationScheduler(
    private val enrollmentRepository: EnrollmentRepository,
    private val enrollmentExpirationProcessor: EnrollmentExpirationProcessor,
    private val waitlistRepository: EnrollmentWaitlistRepository,
    private val clock: Clock,
) {
    @Scheduled(fixedDelayString = $$"${enrollment.payment-expiration.fixed-delay-ms:60000}")
    fun expirePaymentPendingEnrollments() {
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
        countsByCourseId.forEach { (courseId, _) ->
            waitlistRepository.clearSoldOut(courseId)
        }

        if (expiredCount > 0) {
            log.info {
                "결제 대기 만료 처리 완료: ${expiredCount}건, ${countsByCourseId.size}개 강의의 매진 상태를 해제했습니다."
            }
        }
    }
}
