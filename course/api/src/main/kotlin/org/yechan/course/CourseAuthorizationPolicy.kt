package org.yechan.course

import org.springframework.beans.factory.BeanRegistrarDsl
import org.springframework.boot.autoconfigure.AutoConfiguration
import org.springframework.core.Ordered
import org.springframework.http.HttpMethod
import org.yechan.AuthorizeHttpRequestsCustomizer
import org.yechan.PrioritizedAuthorizeHttpRequestsCustomizer
import org.yechan.member.MemberRole

@AutoConfiguration
class CourseAuthorizationPolicy :
    BeanRegistrarDsl({
        registerBean<AuthorizeHttpRequestsCustomizer> {
            PrioritizedAuthorizeHttpRequestsCustomizer(
                Ordered.HIGHEST_PRECEDENCE + 100,
            ) { registry ->
                registry.requestMatchers(HttpMethod.POST, "/api/courses")
                    .hasAnyRole(MemberRole.CREATOR.name)
                registry.requestMatchers(HttpMethod.POST, "/api/courses/{courseId}/open")
                    .hasAnyRole(MemberRole.CREATOR.name)
                registry.requestMatchers(HttpMethod.POST, "/api/courses/{courseId}/close")
                    .hasAnyRole(MemberRole.CREATOR.name)
                registry.requestMatchers(HttpMethod.POST, "/api/courses/{courseId}/enrollments")
                    .hasRole(MemberRole.CLASSMATE.name)
                registry.requestMatchers(HttpMethod.POST, "/api/enrollments/{enrollmentId}/confirm")
                    .hasRole(MemberRole.CLASSMATE.name)
                registry.requestMatchers(HttpMethod.POST, "/api/enrollments/{enrollmentId}/cancel")
                    .hasRole(MemberRole.CLASSMATE.name)
                registry.requestMatchers(HttpMethod.GET, "/api/enrollments/me")
                    .hasRole(MemberRole.CLASSMATE.name)
            }
        }
    })
