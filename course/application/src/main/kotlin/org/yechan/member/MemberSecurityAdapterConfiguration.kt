package org.yechan.member

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.yechan.AccessTokenBlacklist
import java.time.Duration

@Configuration(proxyBeanMethods = false)
class MemberSecurityAdapterConfiguration {
    @Bean
    fun accessTokenBlacklist(
        accessTokenBlacklistRedisRepository: AccessTokenBlacklistRedisRepository,
    ): AccessTokenBlacklist = AccessTokenBlacklistAdapter(accessTokenBlacklistRedisRepository)
}

private class AccessTokenBlacklistAdapter(
    private val accessTokenBlacklistRedisRepository: AccessTokenBlacklistRedisRepository,
) : AccessTokenBlacklist {
    override fun blacklist(
        token: String,
        ttl: Duration,
    ) {
        accessTokenBlacklistRedisRepository.blacklist(token, ttl)
    }

    override fun contains(token: String): Boolean = accessTokenBlacklistRedisRepository.contains(token)
}
