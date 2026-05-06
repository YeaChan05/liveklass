package org.yechan.member

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Table
import org.yechan.BaseEntity
import java.time.LocalDateTime

@Entity
@Table(name = "refresh_tokens")
class RefreshTokenEntity private constructor(
    @field:Column(nullable = false, unique = true)
    var userId: Long,
    @field:Column(nullable = false, unique = true)
    var tokenHash: String,
    @field:Column(nullable = false)
    var expiresAt: LocalDateTime,
) : BaseEntity() {
    companion object {
        fun from(refreshToken: RefreshTokenModel): RefreshTokenEntity = RefreshTokenEntity(
            userId = refreshToken.userId,
            tokenHash = refreshToken.tokenHash,
            expiresAt = refreshToken.expiresAt,
        )
    }

    fun toDomain(): RefreshTokenModel = RefreshTokenModel(
        refreshTokenId = id,
        userId = userId,
        tokenHash = tokenHash,
        expiresAt = expiresAt,
    )
}
