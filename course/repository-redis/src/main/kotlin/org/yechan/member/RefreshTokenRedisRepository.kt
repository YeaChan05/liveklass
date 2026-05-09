package org.yechan.member

import org.springframework.data.redis.core.StringRedisTemplate
import java.time.Duration
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

class RefreshTokenRedisRepository(
    private val redisTemplate: StringRedisTemplate,
) : RefreshTokenRepository {
    override fun replace(refreshToken: RefreshTokenModel) {
        deleteByUserId(refreshToken.userId)

        val ttl = refreshToken.timeToLive()
        if (!ttl.canBeStored()) {
            return
        }

        valueOperations.set(
            RefreshTokenRedisKey.byTokenHash(refreshToken.tokenHash).value,
            RefreshTokenRedisValue.from(refreshToken).serialize(),
            ttl,
        )
        valueOperations.set(
            RefreshTokenRedisKey.byUserId(refreshToken.userId).value,
            refreshToken.tokenHash,
            ttl,
        )
    }

    override fun findByUserId(userId: Long): RefreshTokenModel? {
        val tokenHash = valueOperations.get(RefreshTokenRedisKey.byUserId(userId).value)
            ?: return null
        return findByTokenHash(tokenHash)
    }

    override fun findByTokenHash(tokenHash: String): RefreshTokenModel? {
        val value = valueOperations.get(RefreshTokenRedisKey.byTokenHash(tokenHash).value)
            ?: return null
        return RefreshTokenRedisValue.deserialize(value).toDomain(tokenHash)
    }

    override fun deleteByUserId(userId: Long) {
        redisTemplate.delete(keysByUserId(userId))
    }

    private val valueOperations
        get() = redisTemplate.opsForValue()

    private fun keysByUserId(userId: Long): List<String> {
        val userKey = RefreshTokenRedisKey.byUserId(userId)
        val tokenHash = valueOperations.get(userKey.value)
        return buildList {
            add(userKey.value)
            if (tokenHash != null) {
                add(RefreshTokenRedisKey.byTokenHash(tokenHash).value)
            }
        }
    }

    private fun RefreshTokenModel.timeToLive(): Duration = Duration.between(LocalDateTime.now(), expiresAt)

    private fun Duration.canBeStored(): Boolean = !isNegative && !isZero
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

    fun toDomain(tokenHash: String): RefreshTokenModel = RefreshTokenModelData(
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

        fun deserialize(value: String): RefreshTokenRedisValue {
            val parts = value.split(SEPARATOR, limit = 2)
            return RefreshTokenRedisValue(
                userId = parts[0].toLong(),
                expiresAt = LocalDateTime.parse(parts[1], DateTimeFormatter.ISO_LOCAL_DATE_TIME),
            )
        }
    }
}
