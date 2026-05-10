package org.yechan.auth

import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.security.authentication.AuthenticationProvider
import org.springframework.security.authentication.BadCredentialsException
import org.springframework.security.core.AuthenticationException
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.web.AuthenticationEntryPoint
import org.springframework.web.filter.OncePerRequestFilter
import org.yechan.AccessTokenBlacklist
import org.yechan.TokenParser
import org.yechan.TokenVerifier

class JwtAuthenticationFilter(
    private val parser: TokenParser,
    private val verifier: TokenVerifier,
    private val accessTokenBlacklist: AccessTokenBlacklist,
    private val authenticationEntryPoint: AuthenticationEntryPoint,
    private val authenticationProvider: AuthenticationProvider,
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
            val authenticate = authenticationProvider.authenticate(verifier.verify(token))
            val context = SecurityContextHolder.createEmptyContext()
            context.authentication = authenticate
            SecurityContextHolder.setContext(context)
            filterChain.doFilter(request, response)
        } catch (ex: AuthenticationException) {
            authenticationEntryPoint.commence(request, response, ex)
        } finally {
            SecurityContextHolder.clearContext()
        }
    }
}
