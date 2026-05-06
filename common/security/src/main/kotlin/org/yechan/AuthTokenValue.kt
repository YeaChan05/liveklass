package org.yechan

data class AuthTokenValue(
    val accessToken: String,
    val refreshToken: String,
    val expiresIn: Long,
)
