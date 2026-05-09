package org.yechan.member

import org.springframework.beans.factory.BeanRegistrarDsl
import org.springframework.boot.autoconfigure.AutoConfiguration
import org.springframework.context.annotation.Import
import org.yechan.AccessTokenBlacklist
import java.time.Duration

@Import(MemberSecurityAdapterBeanRegistrar::class)
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
