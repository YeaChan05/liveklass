package org.yechan.member

import org.springframework.data.redis.core.StringRedisTemplate
import java.security.MessageDigest
import java.time.Duration

class AccessTokenBlacklistRedisRepository(
    private val redisTemplate: StringRedisTemplate,
) {
    fun blacklist(
        token: String,
        ttl: Duration,
    ) {
        if (!ttl.isPositive) {
            return
        }

        redisTemplate.opsForValue().set(
            AccessTokenBlacklistRedisKey.byToken(token).value,
            BLACKLISTED,
            ttl,
        )
    }

    fun contains(token: String): Boolean = redisTemplate.hasKey(AccessTokenBlacklistRedisKey.byToken(token).value)

    private companion object {
        const val BLACKLISTED = "1"
    }
}

private sealed interface AccessTokenBlacklistRedisKey {
    val value: String

    data class ByTokenHash(
        val tokenHash: String,
    ) : AccessTokenBlacklistRedisKey {
        override val value: String = "$PREFIX:$tokenHash"
    }

    companion object {
        private const val PREFIX = "course:access-token:blacklist"

        fun byToken(token: String): AccessTokenBlacklistRedisKey = ByTokenHash(token.toTokenHash())
    }
}

private fun String.toTokenHash(): String {
    val bytes = MessageDigest.getInstance("SHA-256").digest(toByteArray())
    return bytes.joinToString("") { "%02x".format(it) }
}
