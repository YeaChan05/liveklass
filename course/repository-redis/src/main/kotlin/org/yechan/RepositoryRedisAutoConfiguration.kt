package org.yechan

import org.springframework.beans.factory.BeanRegistrarDsl
import org.springframework.boot.autoconfigure.AutoConfiguration
import org.yechan.enrollment.EnrollmentWaitlistRedisRepository
import org.yechan.enrollment.EnrollmentWaitlistRepository
import org.yechan.member.AccessTokenBlacklistRedisRepository
import org.yechan.member.AccessTokenBlacklistRepository
import org.yechan.member.AccessTokenBlacklistRepositoryImpl
import org.yechan.member.RefreshTokenRedisRepository
import org.yechan.member.RefreshTokenRepository

@AutoConfiguration
class RepositoryRedisAutoConfiguration :
    BeanRegistrarDsl({
        registerBean<RefreshTokenRepository> {
            RefreshTokenRedisRepository(bean())
        }

        registerBean<EnrollmentWaitlistRepository> {
            EnrollmentWaitlistRedisRepository(bean())
        }

        registerBean<AccessTokenBlacklistRepository> {
            AccessTokenBlacklistRepositoryImpl(bean())
        }

        registerBean<AccessTokenBlacklistRedisRepository> {
            AccessTokenBlacklistRedisRepository(bean())
        }
    })
