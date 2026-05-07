package org.yechan.member

import java.time.Duration

interface AccessTokenBlacklistRepository {
    fun blacklist(
        token: String,
        ttl: Duration,
    )

    fun contains(token: String): Boolean
}
