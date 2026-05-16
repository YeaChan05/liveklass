package org.yechan.enrollment

import org.springframework.boot.context.properties.ConfigurationProperties
import java.time.Duration

@ConfigurationProperties(prefix = "enrollment.waitlist")
data class EnrollmentWaitlistProperties(
    var ttl: Duration = Duration.ofDays(1),
)
