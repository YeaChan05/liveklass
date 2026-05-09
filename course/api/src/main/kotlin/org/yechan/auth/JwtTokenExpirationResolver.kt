package org.yechan.auth

import io.jsonwebtoken.JwtException
import io.jsonwebtoken.Jwts
import io.jsonwebtoken.security.Keys
import org.springframework.security.authentication.BadCredentialsException
import org.yechan.TokenExpirationResolver
import java.nio.charset.StandardCharsets
import java.time.Clock
import java.time.Duration
import java.time.Instant
import javax.crypto.SecretKey

class JwtTokenExpirationResolver(
    salt: String,
    private val clock: Clock = Clock.systemUTC(),
) : TokenExpirationResolver {
    private val secretKey: SecretKey

    init {
        val secret = SECRET + salt
        secretKey = Keys.hmacShaKeyFor(secret.toByteArray(StandardCharsets.UTF_8))
    }

    override fun remainingTime(token: String): Duration {
        try {
            val expiration =
                Jwts.parser()
                    .verifyWith(secretKey)
                    .build()
                    .parseSignedClaims(token)
                    .payload
                    .expiration
                    ?: throw BadCredentialsException("Invalid token")
            val remaining = Duration.between(Instant.now(clock), expiration.toInstant())
            return remaining.takeIf { !it.isNegative } ?: Duration.ZERO
        } catch (ex: JwtException) {
            throw BadCredentialsException("Invalid token", ex)
        } catch (ex: IllegalArgumentException) {
            throw BadCredentialsException("Invalid token", ex)
        }
    }

    private companion object {
        const val SECRET = "member-token-secret-member-token-secret"
    }
}
