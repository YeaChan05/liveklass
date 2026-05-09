package org.yechan.auth

import org.springframework.beans.factory.BeanRegistrarDsl
import org.springframework.boot.autoconfigure.AutoConfiguration
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.yechan.AuthTokenProperties
import org.yechan.CommonSecurityAutoConfiguration
import org.yechan.TokenExpirationResolver
import org.yechan.TokenGenerator
import org.yechan.TokenParser
import org.yechan.TokenVerifier

@EnableConfigurationProperties(
    AuthTokenProperties::class,
)
@AutoConfiguration
class CourseJwtAutoConfiguration

@AutoConfiguration(
    before = [
        CommonSecurityAutoConfiguration::class,
    ],
)
@EnableConfigurationProperties(
    AuthTokenProperties::class,
)
class CourseJwtBeanRegistrar :
    BeanRegistrarDsl({
        registerBean<TokenGenerator> {
            val authTokenProperties = bean<AuthTokenProperties>()

            JwtTokenGenerator(
                authTokenProperties.salt,
                authTokenProperties.accessExpiresIn,
                authTokenProperties.refreshExpiresIn,
            )
        }

        registerBean<TokenParser> {
            JwtTokenParser()
        }

        registerBean<TokenVerifier> {
            JwtTokenVerifier(bean<AuthTokenProperties>().salt)
        }

        registerBean<TokenExpirationResolver> {
            JwtTokenExpirationResolver(bean<AuthTokenProperties>().salt)
        }
    })
