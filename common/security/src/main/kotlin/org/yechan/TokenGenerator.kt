package org.yechan

fun interface TokenGenerator {
    fun generate(memberId: Long?): AuthTokenValue
}
