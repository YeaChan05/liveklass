package org.yechan.auth

import org.springframework.beans.factory.BeanRegistrarDsl
import org.springframework.boot.autoconfigure.AutoConfiguration
import org.yechan.AccessTokenBlacklist
import org.yechan.member.AccessTokenBlacklistRepository
import java.time.Duration

@AutoConfiguration
class MemberSecurityAdapterConfiguration
class MemberSecurityAdapterBeanRegistrar :
    BeanRegistrarDsl({
        registerBean<AccessTokenBlacklist> {
            AccessTokenBlacklistAdapter(bean())
        }
    })

private class AccessTokenBlacklistAdapter(
    private val accessTokenBlacklistRepository: AccessTokenBlacklistRepository,
) : AccessTokenBlacklist {
    override fun blacklist(
        token: String,
        ttl: Duration,
    ) {
        accessTokenBlacklistRepository.blacklist(token, ttl)
    }

    override fun contains(token: String): Boolean = accessTokenBlacklistRepository.contains(token)
}
