package org.yechan

import java.time.Duration

interface AccessTokenBlacklist {
    fun blacklist(
        token: String,
        ttl: Duration,
    )

    fun contains(token: String): Boolean
}

object NoOpAccessTokenBlacklist : AccessTokenBlacklist {
    override fun blacklist(
        token: String,
        ttl: Duration,
    ) {
    }

    override fun contains(token: String): Boolean = false
}
