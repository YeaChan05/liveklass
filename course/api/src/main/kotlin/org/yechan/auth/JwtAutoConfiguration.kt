package org.yechan.auth

import org.springframework.beans.factory.BeanRegistrarDsl
import org.springframework.boot.autoconfigure.AutoConfiguration
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.boot.web.servlet.FilterRegistrationBean
import org.springframework.context.annotation.Import
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter
import org.yechan.AccessTokenBlacklist
import org.yechan.AuthTokenProperties
import org.yechan.CommonSecurityAutoConfiguration
import org.yechan.SecurityFilterChainCustomizer
import org.yechan.TokenExpirationResolver
import org.yechan.TokenGenerator
import org.yechan.TokenParser
import org.yechan.TokenVerifier
import org.yechan.member.MemberAuthenticationProvider

@AutoConfiguration(
    before = [
        CommonSecurityAutoConfiguration::class,
    ],
)
@EnableConfigurationProperties(
    AuthTokenProperties::class,
)
@Import(JwtBeanRegistrar::class)
class JwtAutoConfiguration

class JwtBeanRegistrar :
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

        registerBean<MemberAuthenticationProvider> {
            MemberAuthenticationProvider(
                memberAuthUseCase = bean(),
            )
        }

        registerBean<JwtAuthenticationFilter> {
            JwtAuthenticationFilter(
                parser = bean<TokenParser>(),
                verifier = bean<TokenVerifier>(),
                accessTokenBlacklist = bean<AccessTokenBlacklist>(),
                authenticationEntryPoint = bean(),
                authenticationProvider = bean<MemberAuthenticationProvider>(),
            )
        }

        registerBean<FilterRegistrationBean<JwtAuthenticationFilter>> {
            FilterRegistrationBean(bean<JwtAuthenticationFilter>()).apply {
                isEnabled = false
            }
        }

        registerBean<SecurityFilterChainCustomizer> {
            SecurityFilterChainCustomizer { http ->
                http.addFilterBefore(
                    bean<JwtAuthenticationFilter>(),
                    UsernamePasswordAuthenticationFilter::class.java,
                )
            }
        }
    })
