package org.yechan.member

import org.springframework.stereotype.Repository
import java.time.Duration

@Repository
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
