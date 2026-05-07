package org.yechan.member

import org.springframework.beans.factory.BeanRegistrarDsl
import org.springframework.boot.autoconfigure.AutoConfiguration
import org.yechan.AccessTokenBlacklist
import java.time.Duration

@AutoConfiguration
class MemberSecurityAdapterConfiguration :
    BeanRegistrarDsl({
        registerBean<AccessTokenBlacklist> {
            AccessTokenBlacklistAdapter(bean())
        }
    })

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
