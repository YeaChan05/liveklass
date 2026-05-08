package org.yechan

import org.springframework.beans.factory.BeanRegistrarDsl
import org.springframework.boot.autoconfigure.AutoConfiguration
import org.yechan.member.AccessTokenBlacklistRedisRepository
import org.yechan.member.RefreshTokenRedisRepository
import org.yechan.member.RefreshTokenRepository

@AutoConfiguration
class RepositoryRedisAutoConfiguration :
    BeanRegistrarDsl({
        registerBean<RefreshTokenRepository> {
            RefreshTokenRedisRepository(bean())
        }
        registerBean<AccessTokenBlacklistRedisRepository> {
            AccessTokenBlacklistRedisRepository(bean())
        }
    })
