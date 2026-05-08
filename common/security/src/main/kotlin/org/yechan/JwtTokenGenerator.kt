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

    override fun generate(
        memberId: Long?,
        roles: Set<String>,
    ): AuthTokenValue {
        val issuedAt = Instant.now()
        val actualMemberId = requireNotNull(memberId)
        val accessToken = createToken(actualMemberId, roles, issuedAt, accessExpiresIn)
        val refreshToken = createToken(actualMemberId, roles, issuedAt, refreshExpiresIn)
        return AuthTokenValue(accessToken, refreshToken, accessExpiresIn)
    }

    private fun createToken(
        memberId: Long,
        roles: Set<String>,
        issuedAt: Instant,
        expiresInSeconds: Long,
    ): String = Jwts.builder()
        .subject(memberId.toString())
        .claim(ROLES_CLAIM, roles.toList())
        .issuedAt(Date.from(issuedAt))
        .expiration(Date.from(issuedAt.plusSeconds(expiresInSeconds)))
        .signWith(secretKey)
        .compact()

    private companion object {
        const val SECRET = "member-token-secret-member-token-secret"
        const val ROLES_CLAIM = "roles"
    }
}
