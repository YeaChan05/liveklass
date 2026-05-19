package org.yechan.course

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
class CourseOpenEndpointPolicy :
    BeanRegistrarDsl({
        registerBean<AuthorizeHttpRequestsCustomizer> {
            PrioritizedAuthorizeHttpRequestsCustomizer(
                Ordered.HIGHEST_PRECEDENCE,
                ApplicationOpenEndpointsAuthorizeHttpRequestsCustomizer(
                    courseApplicationOpenEndpointPolicy(),
                ),
            )
        }
    })

private fun courseApplicationOpenEndpointPolicy(): StaticApplicationOpenEndpointPolicy = StaticApplicationOpenEndpointPolicy(
    false,
    listOf(
        OpenEndpointMatcher(HttpMethod.GET, "/api/courses"),
        OpenEndpointMatcher(HttpMethod.GET, "/api/courses/{courseId}"),
    ),
)
