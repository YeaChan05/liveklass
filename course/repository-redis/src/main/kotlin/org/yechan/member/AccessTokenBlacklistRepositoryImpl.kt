package org.yechan.member

import java.time.Duration

class AccessTokenBlacklistRepositoryImpl(
    private val accessTokenBlacklistRedisRepository: AccessTokenBlacklistRedisRepository,
) : AccessTokenBlacklistRepository {
    override fun blacklist(
        token: String,
        ttl: Duration,
    ) {
        accessTokenBlacklistRedisRepository.blacklist(token, ttl)
    }

    override fun contains(token: String): Boolean = accessTokenBlacklistRedisRepository.contains(token)
}
