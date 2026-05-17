package org.yechan

import org.springframework.beans.factory.BeanRegistrarDsl
import org.springframework.boot.autoconfigure.AutoConfiguration
import org.springframework.core.Ordered
import org.springframework.http.HttpMethod

@AutoConfiguration
class SwaggerOpenEndpointPolicy :
    BeanRegistrarDsl({
        registerBean<AuthorizeHttpRequestsCustomizer> {
            PrioritizedAuthorizeHttpRequestsCustomizer(
                Ordered.HIGHEST_PRECEDENCE,
                ApplicationOpenEndpointsAuthorizeHttpRequestsCustomizer(
                    swaggerApplicationOpenEndpointPolicy(),
                ),
            )
        }
    })

private fun swaggerApplicationOpenEndpointPolicy(): StaticApplicationOpenEndpointPolicy = StaticApplicationOpenEndpointPolicy(
    additionalMatchers =
    listOf(
        OpenEndpointMatcher(HttpMethod.GET, "/v3/api-docs"),
        OpenEndpointMatcher(HttpMethod.GET, "/v3/api-docs/**"),
        OpenEndpointMatcher(HttpMethod.GET, "/v3/api-docs.yaml"),
        OpenEndpointMatcher(HttpMethod.GET, "/swagger-ui.html"),
        OpenEndpointMatcher(HttpMethod.GET, "/swagger-ui/**"),
    ),
)
