package org.yechan

import jakarta.servlet.http.HttpServletRequest

fun interface TokenParser {
    fun parse(request: HttpServletRequest): String?
}

object NoOpTokenParser : TokenParser {
    override fun parse(request: HttpServletRequest): String? = null
}
