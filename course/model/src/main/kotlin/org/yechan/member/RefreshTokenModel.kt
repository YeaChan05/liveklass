package org.yechan.member

import java.time.LocalDateTime

interface RefreshTokenIdentifier {
    val refreshTokenId: Long?
}

interface RefreshTokenProps {
    val userId: Long
    val tokenHash: String
    val expiresAt: LocalDateTime
}

interface RefreshTokenModel :
    RefreshTokenProps,
    RefreshTokenIdentifier

data class RefreshTokenModelData(
    override val refreshTokenId: Long? = null,
    override val userId: Long,
    override val tokenHash: String,
    override val expiresAt: LocalDateTime,
) : RefreshTokenModel

fun verifyTokenExpiry(refreshToken: RefreshTokenModel) {
    if (refreshToken.expiresAt.isBefore(LocalDateTime.now())) {
        throw InvalidRefreshTokenException()
    }
}

fun validateUserIdMatch(refreshToken: RefreshTokenModel, userId: Long) {
    if (refreshToken.userId != userId) {
        throw InvalidRefreshTokenException()
    }
}
