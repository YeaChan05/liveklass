package org.yechan.member

import org.springframework.beans.factory.BeanRegistrarDsl
import org.springframework.boot.autoconfigure.AutoConfiguration
import org.springframework.core.Ordered
import org.springframework.http.HttpMethod
import org.yechan.ApplicationOpenEndpointsAuthorizeHttpRequestsCustomizer
import org.yechan.AuthorizeHttpRequestsCustomizer
import org.yechan.OpenEndpointMatcher
import org.yechan.PrioritizedAuthorizeHttpRequestsCustomizer
import org.yechan.StaticApplicationOpenEndpointPolicy

@AutoConfiguration
class MemberAuthOpenEndpointPolicy :
    BeanRegistrarDsl({
        registerBean<AuthorizeHttpRequestsCustomizer> {
            PrioritizedAuthorizeHttpRequestsCustomizer(
                Ordered.HIGHEST_PRECEDENCE,
                ApplicationOpenEndpointsAuthorizeHttpRequestsCustomizer(memberApplicationOpenEndpointPolicy()),
            )
        }
    })

private fun memberApplicationOpenEndpointPolicy(): StaticApplicationOpenEndpointPolicy = StaticApplicationOpenEndpointPolicy(
    additionalMatchers =
    listOf(
        OpenEndpointMatcher(HttpMethod.POST, "/api/auth/signup"),
        OpenEndpointMatcher(HttpMethod.POST, "/api/auth/login"),
        OpenEndpointMatcher(HttpMethod.POST, "/api/auth/token/refresh"),
    ),
)
