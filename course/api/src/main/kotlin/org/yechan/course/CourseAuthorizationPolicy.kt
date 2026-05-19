package org.yechan.course

import org.springframework.beans.factory.BeanRegistrarDsl
import org.springframework.boot.autoconfigure.AutoConfiguration
import org.springframework.core.Ordered
import org.springframework.http.HttpMethod
import org.springframework.http.HttpMethod.POST
import org.yechan.AuthorizeHttpRequestsCustomizer
import org.yechan.PrioritizedAuthorizeHttpRequestsCustomizer
import org.yechan.member.MemberRole
import org.yechan.member.MemberRole.CLASSMATE

@AutoConfiguration
class CourseAuthorizationPolicy :
    BeanRegistrarDsl({
        registerBean<AuthorizeHttpRequestsCustomizer> {
            PrioritizedAuthorizeHttpRequestsCustomizer(
                Ordered.HIGHEST_PRECEDENCE + 100,
            ) { registry ->
                registry.requestMatchers(POST, "/api/enrollments/{enrollmentId}/confirm")
                    .hasRole(CLASSMATE.name)
                registry.requestMatchers(POST, "/api/enrollments/{enrollmentId}/cancel")
                    .hasRole(CLASSMATE.name)
                registry.requestMatchers(HttpMethod.DELETE, "/api/enrollments/waitlist/{courseId}")
                    .hasRole(CLASSMATE.name)
                registry.requestMatchers(HttpMethod.GET, "/api/enrollments/me")
                    .hasRole(CLASSMATE.name)
                registry.requestMatchers(POST, "/api/courses")
                    .hasAnyRole(MemberRole.CREATOR.name)
                registry.requestMatchers(POST, "/api/courses/{courseId}/open")
                    .hasAnyRole(MemberRole.CREATOR.name)
                registry.requestMatchers(POST, "/api/courses/{courseId}/close")
                    .hasAnyRole(MemberRole.CREATOR.name)
                registry.requestMatchers(POST, "/api/courses/{courseId}/enrollments")
                    .hasRole(CLASSMATE.name)
            }
        }
    })
