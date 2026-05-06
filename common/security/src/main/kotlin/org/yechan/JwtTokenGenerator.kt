package org.yechan

import io.jsonwebtoken.Jwts
import io.jsonwebtoken.security.Keys
import java.nio.charset.StandardCharsets
import java.time.Instant
import java.util.Date
import javax.crypto.SecretKey

class JwtTokenGenerator(
    salt: String,
    private val accessExpiresIn: Long,
    private val refreshExpiresIn: Long,
) : TokenGenerator {
    private val secretKey: SecretKey

    init {
        val secret = SECRET + salt
        secretKey = Keys.hmacShaKeyFor(secret.toByteArray(StandardCharsets.UTF_8))
    }

    override fun generate(memberId: Long?): AuthTokenValue {
        val issuedAt = Instant.now()
        val actualMemberId = requireNotNull(memberId)
        val accessToken = createToken(actualMemberId, issuedAt, accessExpiresIn)
        val refreshToken = createToken(actualMemberId, issuedAt, refreshExpiresIn)
        return AuthTokenValue(accessToken, refreshToken, accessExpiresIn)
    }

    private fun createToken(
        memberId: Long,
        issuedAt: Instant,
        expiresInSeconds: Long,
    ): String = Jwts.builder()
        .subject(memberId.toString())
        .issuedAt(Date.from(issuedAt))
        .expiration(Date.from(issuedAt.plusSeconds(expiresInSeconds)))
        .signWith(secretKey)
        .compact()

    private companion object {
        const val SECRET = "member-token-secret-member-token-secret"
    }
}
