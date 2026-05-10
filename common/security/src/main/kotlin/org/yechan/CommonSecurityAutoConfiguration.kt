package org.yechan

import org.springframework.beans.factory.BeanRegistrarDsl
import org.springframework.boot.autoconfigure.AutoConfiguration
import org.springframework.boot.security.autoconfigure.SecurityAutoConfiguration
import org.springframework.boot.security.autoconfigure.web.servlet.ServletWebSecurityAutoConfiguration
import org.springframework.context.annotation.Import
import org.springframework.core.Ordered
import org.springframework.security.access.hierarchicalroles.RoleHierarchy
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.annotation.web.configurers.CsrfConfigurer
import org.springframework.security.config.annotation.web.configurers.FormLoginConfigurer
import org.springframework.security.config.http.SessionCreationPolicy
import org.springframework.security.web.AuthenticationEntryPoint
import org.springframework.security.web.SecurityFilterChain
import org.springframework.security.web.access.AccessDeniedHandler

@AutoConfiguration(
    before = [
        SecurityAutoConfiguration::class,
        ServletWebSecurityAutoConfiguration::class,
    ],
)
@Import(CommonSecurityBeanRegistrar::class)
class CommonSecurityAutoConfiguration

fun interface SecurityFilterChainCustomizer {
    fun customize(http: HttpSecurity)
}

class CommonSecurityBeanRegistrar :
    BeanRegistrarDsl({
        registerBean<AccessTokenBlacklist>(fallback = true) {
            NoOpAccessTokenBlacklist
        }

        registerBean<AuthenticationEntryPoint> {
            DefaultAuthenticationEntryPoint()
        }

        registerBean<AccessDeniedHandler> {
            DefaultAccessDeniedHandler()
        }

        registerBean<AuthorizeHttpRequestsCustomizer> {
            PrioritizedAuthorizeHttpRequestsCustomizer(
                Ordered.LOWEST_PRECEDENCE,
            ) { registry ->
                registry.anyRequest().authenticated()
            }
        }

        registerBean<SecurityFilterChain> {
            val authorizeHttpRequestsCustomizers =
                beanProvider<AuthorizeHttpRequestsCustomizer>()

            val securityFilterChainCustomizers =
                beanProvider<SecurityFilterChainCustomizer>()

            val roleHierarchy =
                beanProvider<RoleHierarchy>().ifAvailable

            val http =
                bean<HttpSecurity>()
                    .formLogin(FormLoginConfigurer<HttpSecurity>::disable)
                    .csrf(CsrfConfigurer<HttpSecurity>::disable)
                    .sessionManagement { session ->
                        session.sessionCreationPolicy(SessionCreationPolicy.STATELESS)
                    }
                    .exceptionHandling { handler ->
                        handler.authenticationEntryPoint(bean())
                        handler.accessDeniedHandler(bean())
                    }
                    .authorizeHttpRequests { registry ->
                        authorizeHttpRequestsCustomizers
                            .orderedStream()
                            .forEach { customizer ->
                                customizer.customize(registry)
                            }
                    }
                    .let { http ->
                        if (roleHierarchy != null) {
                            http.setSharedObject(RoleHierarchy::class.java, roleHierarchy)
                        }
                        http
                    }

            securityFilterChainCustomizers
                .orderedStream()
                .forEach { customizer ->
                    customizer.customize(http)
                }

            http.build()
        }
    })
