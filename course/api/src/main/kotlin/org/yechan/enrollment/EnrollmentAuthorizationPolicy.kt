package org.yechan.enrollment

import org.springframework.beans.factory.BeanRegistrarDsl
import org.springframework.boot.autoconfigure.AutoConfiguration
import org.springframework.core.Ordered
import org.springframework.http.HttpMethod
import org.springframework.http.HttpMethod.POST
import org.yechan.AuthorizeHttpRequestsCustomizer
import org.yechan.PrioritizedAuthorizeHttpRequestsCustomizer
import org.yechan.member.MemberRole.CLASSMATE

@AutoConfiguration
class EnrollmentAuthorizationPolicy :
    BeanRegistrarDsl({
        registerBean<AuthorizeHttpRequestsCustomizer> {
            PrioritizedAuthorizeHttpRequestsCustomizer(
                Ordered.HIGHEST_PRECEDENCE + 100,
            ) { registry ->
                registry.requestMatchers(POST, "/api/enrollments/{enrollmentId}/confirm")
                    .hasRole(CLASSMATE.name)
                registry.requestMatchers(POST, "/api/enrollments/{enrollmentId}/cancel")
                    .hasRole(CLASSMATE.name)
                registry.requestMatchers(HttpMethod.GET, "/api/enrollments/me")
                    .hasRole(CLASSMATE.name)
            }
        }
    })
