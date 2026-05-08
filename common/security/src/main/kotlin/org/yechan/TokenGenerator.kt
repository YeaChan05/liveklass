package org.yechan

interface TokenGenerator {
    fun generate(
        memberId: Long?,
        roles: Set<String>,
    ): AuthTokenValue
}
