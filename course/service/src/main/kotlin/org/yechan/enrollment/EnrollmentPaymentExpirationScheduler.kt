package org.yechan.enrollment

import org.springframework.scheduling.annotation.Scheduled
import java.time.Clock
import java.time.LocalDateTime

class EnrollmentPaymentExpirationScheduler(
    private val enrollmentExpirationUseCase: EnrollmentExpirationUseCase,
    private val clock: Clock,
) {
    @Scheduled(
        fixedDelayString = $$"${enrollment.payment-expiration.fixed-delay-ms:60000}",
    )
    fun expirePaymentPendingEnrollments() {
        enrollmentExpirationUseCase.expirePaymentPendingEnrollments(
            now = LocalDateTime.now(clock),
        )
    }
}
