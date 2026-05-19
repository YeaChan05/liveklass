package org.yechan

import org.springframework.beans.factory.BeanRegistrarDsl
import org.springframework.boot.autoconfigure.AutoConfiguration
import org.springframework.core.Ordered
import org.springframework.http.HttpMethod

@AutoConfiguration
class ActuatorOpenEndpointPolicy :
    BeanRegistrarDsl({
        registerBean<AuthorizeHttpRequestsCustomizer> {
            PrioritizedAuthorizeHttpRequestsCustomizer(
                Ordered.HIGHEST_PRECEDENCE,
                ApplicationOpenEndpointsAuthorizeHttpRequestsCustomizer(
                    actuatorOpenEndpointPolicy(),
                ),
            )
        }
    })

private fun actuatorOpenEndpointPolicy(): StaticApplicationOpenEndpointPolicy = StaticApplicationOpenEndpointPolicy(
    false,
    listOf(
        OpenEndpointMatcher(HttpMethod.GET, "/actuator/health"),
        OpenEndpointMatcher(HttpMethod.GET, "/actuator/info"),
        OpenEndpointMatcher(HttpMethod.GET, "/actuator/prometheus"),
    ),
)
