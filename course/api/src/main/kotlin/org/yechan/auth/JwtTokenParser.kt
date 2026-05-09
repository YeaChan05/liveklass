package org.yechan.auth

import jakarta.servlet.http.HttpServletRequest
import org.springframework.http.HttpHeaders
import org.springframework.util.StringUtils
import org.yechan.TokenParser

class JwtTokenParser : TokenParser {
    override fun parse(request: HttpServletRequest): String? {
        val header = request.getHeader(HttpHeaders.AUTHORIZATION)
        if (!StringUtils.hasText(header) || !header.startsWith(BEARER)) {
            return null
        }
        return header.substring(BEARER.length)
    }

    private companion object {
        const val BEARER = "Bearer "
    }
}
