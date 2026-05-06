package org.yechan.member

import org.springframework.data.jpa.repository.JpaRepository

interface RefreshTokenJpaRepository : JpaRepository<RefreshTokenEntity, Long> {
    fun findByUserId(userId: Long): RefreshTokenEntity?

    fun findByTokenHash(tokenHash: String): RefreshTokenEntity?

    fun deleteByUserId(userId: Long)
}
