package org.yechan

import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.security.authentication.BadCredentialsException
import org.springframework.security.core.AuthenticationException
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.web.AuthenticationEntryPoint
import org.springframework.web.filter.OncePerRequestFilter

class JwtAuthenticationFilter(
    private val parser: TokenParser,
    private val verifier: TokenVerifier,
    private val accessTokenBlacklist: AccessTokenBlacklist,
    private val authenticationEntryPoint: AuthenticationEntryPoint,
) : OncePerRequestFilter() {
    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain,
    ) {
        val token = parser.parse(request)
        if (token == null) {
            filterChain.doFilter(request, response)
            return
        }

        try {
            if (accessTokenBlacklist.contains(token)) {
                throw BadCredentialsException("Blacklisted access token")
            }
            val authentication = verifier.verify(token)
            val context = SecurityContextHolder.createEmptyContext()
            context.authentication = authentication
            SecurityContextHolder.setContext(context)
            filterChain.doFilter(request, response)
        } catch (ex: AuthenticationException) {
            authenticationEntryPoint.commence(request, response, ex)
        } finally {
            SecurityContextHolder.clearContext()
        }
    }
}
