package org.yechan

import org.springframework.core.MethodParameter
import org.springframework.security.authentication.AnonymousAuthenticationToken
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.web.bind.support.WebDataBinderFactory
import org.springframework.web.context.request.NativeWebRequest
import org.springframework.web.method.support.HandlerMethodArgumentResolver
import org.springframework.web.method.support.ModelAndViewContainer

internal class LoginUserIdArgumentResolver : HandlerMethodArgumentResolver {
    override fun supportsParameter(parameter: MethodParameter): Boolean = parameter.hasParameterAnnotation(LoginUserId::class.java) &&
        (parameter.parameterType == Long::class.javaObjectType || parameter.parameterType == Long::class.javaPrimitiveType)

    override fun resolveArgument(
        parameter: MethodParameter,
        mavContainer: ModelAndViewContainer?,
        webRequest: NativeWebRequest,
        binderFactory: WebDataBinderFactory?,
    ): Any {
        val authentication = SecurityContextHolder.getContext().authentication
        if (authentication == null || authentication is AnonymousAuthenticationToken) {
            throw BusinessException(Status.AUTHENTICATION_FAILED, "Unauthorized")
        }

        return authentication.name.toLongOrNull()
            ?: throw BusinessException(Status.BAD_REQUEST, "Invalid user id")
    }
}
