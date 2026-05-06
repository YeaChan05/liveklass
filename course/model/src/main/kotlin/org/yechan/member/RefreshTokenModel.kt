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

data class RefreshTokenModel(
    override val refreshTokenId: Long? = null,
    override val userId: Long,
    override val tokenHash: String,
    override val expiresAt: LocalDateTime,
) : RefreshTokenProps,
    RefreshTokenIdentifier
