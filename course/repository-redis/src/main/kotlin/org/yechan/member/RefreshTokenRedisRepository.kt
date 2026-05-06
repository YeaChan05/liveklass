package org.yechan.member

import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.stereotype.Repository
import java.time.Duration
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

@Repository
class RefreshTokenRedisRepository(
    private val redisTemplate: StringRedisTemplate,
) : RefreshTokenRepository {
    override fun replace(refreshToken: RefreshTokenModel) {
        deleteByUserId(refreshToken.userId)

        val tokenKey = RefreshTokenRedisKey.byTokenHash(refreshToken.tokenHash)
        val userKey = RefreshTokenRedisKey.byUserId(refreshToken.userId)
        val value = RefreshTokenRedisValue.from(refreshToken)
        val ttl = Duration.between(LocalDateTime.now(), refreshToken.expiresAt)

        if (ttl.isNegative || ttl.isZero) {
            return
        }

        redisTemplate.opsForValue().set(tokenKey.value, value.serialize(), ttl)
        redisTemplate.opsForValue().set(userKey.value, refreshToken.tokenHash, ttl)
    }

    override fun findByUserId(userId: Long): RefreshTokenModel? {
        val tokenHash = redisTemplate.opsForValue().get(RefreshTokenRedisKey.byUserId(userId).value)
            ?: return null
        return findByTokenHash(tokenHash)
    }

    override fun findByTokenHash(tokenHash: String): RefreshTokenModel? {
        val value = redisTemplate.opsForValue().get(RefreshTokenRedisKey.byTokenHash(tokenHash).value)
            ?: return null
        return RefreshTokenRedisValue.deserialize(tokenHash, value).toDomain()
    }

    override fun deleteByUserId(userId: Long) {
        val userKey = RefreshTokenRedisKey.byUserId(userId)
        val tokenHash = redisTemplate.opsForValue().get(userKey.value)
        val keys = buildList {
            add(userKey.value)
            if (tokenHash != null) {
                add(RefreshTokenRedisKey.byTokenHash(tokenHash).value)
            }
        }
        redisTemplate.delete(keys)
    }
}

private sealed interface RefreshTokenRedisKey {
    val value: String

    data class ByUserId(
        val userId: Long,
    ) : RefreshTokenRedisKey {
        override val value: String = "$PREFIX:user:$userId"
    }

    data class ByTokenHash(
        val tokenHash: String,
    ) : RefreshTokenRedisKey {
        override val value: String = "$PREFIX:token:$tokenHash"
    }

    companion object {
        private const val PREFIX = "course:refresh-token"

        fun byUserId(userId: Long): RefreshTokenRedisKey = ByUserId(userId)

        fun byTokenHash(tokenHash: String): RefreshTokenRedisKey = ByTokenHash(tokenHash)
    }
}

private data class RefreshTokenRedisValue(
    val userId: Long,
    val expiresAt: LocalDateTime,
) {
    fun serialize(): String = listOf(userId.toString(), expiresAt.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME))
        .joinToString(SEPARATOR)

    fun toDomain(tokenHash: String): RefreshTokenModel = RefreshTokenModel(
        userId = userId,
        tokenHash = tokenHash,
        expiresAt = expiresAt,
    )

    companion object {
        private const val SEPARATOR = "|"

        fun from(refreshToken: RefreshTokenModel): RefreshTokenRedisValue = RefreshTokenRedisValue(
            userId = refreshToken.userId,
            expiresAt = refreshToken.expiresAt,
        )

        fun deserialize(
            tokenHash: String,
            value: String,
        ): RefreshTokenWithHash {
            val parts = value.split(SEPARATOR, limit = 2)
            return RefreshTokenWithHash(
                tokenHash = tokenHash,
                value = RefreshTokenRedisValue(
                    userId = parts[0].toLong(),
                    expiresAt = LocalDateTime.parse(parts[1], DateTimeFormatter.ISO_LOCAL_DATE_TIME),
                ),
            )
        }
    }
}

private data class RefreshTokenWithHash(
    val tokenHash: String,
    val value: RefreshTokenRedisValue,
) {
    fun toDomain(): RefreshTokenModel = value.toDomain(tokenHash)
}
