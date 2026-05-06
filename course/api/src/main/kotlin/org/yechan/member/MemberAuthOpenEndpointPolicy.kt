package org.yechan.member

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.core.Ordered
import org.springframework.http.HttpMethod
import org.yechan.ApplicationOpenEndpointPolicy
import org.yechan.ApplicationOpenEndpointsAuthorizeHttpRequestsCustomizer
import org.yechan.AuthorizeHttpRequestsCustomizer
import org.yechan.OpenEndpointMatcher
import org.yechan.PrioritizedAuthorizeHttpRequestsCustomizer
import org.yechan.StaticApplicationOpenEndpointPolicy

@Configuration
class MemberAuthOpenEndpointPolicy {
    @Bean
    fun memberApplicationOpenEndpointPolicy(): ApplicationOpenEndpointPolicy = StaticApplicationOpenEndpointPolicy(
        additionalMatchers =
        listOf(
            OpenEndpointMatcher(HttpMethod.POST, "/api/v1/auth/signup"),
            OpenEndpointMatcher(HttpMethod.POST, "/api/v1/auth/login"),
            OpenEndpointMatcher(HttpMethod.POST, "/api/v1/auth/token/refresh"),
        ),
    )

    @Bean
    fun memberOpenEndpointCustomizer(policy: ApplicationOpenEndpointPolicy): AuthorizeHttpRequestsCustomizer = PrioritizedAuthorizeHttpRequestsCustomizer(
        Ordered.HIGHEST_PRECEDENCE,
        ApplicationOpenEndpointsAuthorizeHttpRequestsCustomizer(policy),
    )
}
