package org.yechan

import org.springframework.beans.factory.BeanRegistrarDsl
import org.springframework.boot.autoconfigure.AutoConfiguration
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Import
import org.yechan.enrollment.EnrollmentWaitlistProperties
import org.yechan.enrollment.EnrollmentWaitlistRedisRepository
import org.yechan.enrollment.EnrollmentWaitlistRepository
import org.yechan.member.AccessTokenBlacklistRedisRepository
import org.yechan.member.AccessTokenBlacklistRepository
import org.yechan.member.AccessTokenBlacklistRepositoryImpl
import org.yechan.member.RefreshTokenRedisRepository
import org.yechan.member.RefreshTokenRepository

@AutoConfiguration
@Import(RepositoryRedisBeanRegistrar::class)
@EnableConfigurationProperties(EnrollmentWaitlistProperties::class)
class RepositoryRedisAutoConfiguration

@AutoConfiguration
class RepositoryRedisBeanRegistrar :
    BeanRegistrarDsl({
        registerBean<RefreshTokenRepository> {
            RefreshTokenRedisRepository(bean())
        }

        registerBean<EnrollmentWaitlistRepository> {
            EnrollmentWaitlistRedisRepository(
                bean(),
                bean<EnrollmentWaitlistProperties>().ttl,
            )
        }

        registerBean<AccessTokenBlacklistRepository> {
            AccessTokenBlacklistRepositoryImpl(bean())
        }

        registerBean<AccessTokenBlacklistRedisRepository> {
            AccessTokenBlacklistRedisRepository(bean())
        }
    })
