package org.yechan

import jakarta.validation.constraints.Min
import jakarta.validation.constraints.NotBlank
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.validation.annotation.Validated

@ConfigurationProperties(prefix = "auth.token")
@Validated
data class AuthTokenProperties(
    @field:NotBlank
    val salt: String,
    @field:Min(1)
    val accessExpiresIn: Long,
    @field:Min(1)
    val refreshExpiresIn: Long,
)
