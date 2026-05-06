package org.yechan.member

interface RefreshTokenRepository {
    fun replace(refreshToken: RefreshTokenModel)

    fun findByUserId(userId: Long): RefreshTokenModel?

    fun findByTokenHash(tokenHash: String): RefreshTokenModel?

    fun deleteByUserId(userId: Long)
}
