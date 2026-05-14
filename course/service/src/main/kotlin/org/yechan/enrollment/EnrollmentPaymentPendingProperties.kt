package org.yechan.enrollment

import org.springframework.boot.context.properties.ConfigurationProperties
import java.time.Duration

@ConfigurationProperties(prefix = "enrollment.payment-pending")
data class EnrollmentPaymentPendingProperties(
    var expiresIn: Duration = Duration.ofMinutes(10),
)
