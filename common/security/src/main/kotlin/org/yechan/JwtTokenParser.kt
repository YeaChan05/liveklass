package org.yechan

import jakarta.servlet.http.HttpServletRequest
import org.springframework.http.HttpHeaders
import org.springframework.util.StringUtils

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
