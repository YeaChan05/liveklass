package org.yechan

import java.time.Duration

fun interface TokenExpirationResolver {
    fun remainingTime(token: String): Duration
}
