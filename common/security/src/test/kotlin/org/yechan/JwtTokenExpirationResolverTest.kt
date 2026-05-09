package org.yechan

import io.jsonwebtoken.Jwts
import io.jsonwebtoken.security.Keys
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.security.authentication.BadCredentialsException
import org.yechan.auth.JwtTokenExpirationResolver
import org.yechan.auth.JwtTokenGenerator
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneId

class JwtTokenExpirationResolverTest {
    private val salt = "test-salt"
    private val accessExpiresIn = 3600L
    private val refreshExpiresIn = 86400L
    private val tokenGenerator = JwtTokenGenerator(salt, accessExpiresIn, refreshExpiresIn)

    @Test
    fun `정상적인 토큰의 남은 시간을 계산한다`() {
        // Arrange
        // 토큰 생성 시점과 동일한 시점으로 고정된 시계로 생성된 토큰
        val now = Instant.now()
        val currentClock = Clock.fixed(now, ZoneId.of("UTC"))
        val currentResolver = JwtTokenExpirationResolver(salt, currentClock)

        val token = tokenGenerator.generate(1L, setOf("USER")).accessToken

        // Act
        val remaining = currentResolver.remainingTime(token)

        // Assert
        // 생성 직후이므로 약 3600초 남아 있어야 함
        assertThat(remaining.seconds).isGreaterThan(3590)
        assertThat(remaining.seconds).isLessThanOrEqualTo(3600)
    }

    @Test
    fun `만료된 토큰의 경우 Duration ZERO를 반환한다`() {
        // Arrange
        val token = tokenGenerator.generate(1L, setOf("USER")).accessToken

        // 2시간 후로 고정된 시계 (만료 시간 1시간을 지남)
        val futureInstant = Instant.now().plus(Duration.ofHours(2))
        val clock = Clock.fixed(futureInstant, ZoneId.of("UTC"))
        val resolver = JwtTokenExpirationResolver(salt, clock)

        // Act
        val remaining = resolver.remainingTime(token)

        // Assert
        assertThat(remaining).isEqualTo(Duration.ZERO)
    }

    @Test
    fun `잘못된 형식의 토큰에 대해 BadCredentialsException 발생한다`() {
        // Arrange
        val resolver = JwtTokenExpirationResolver(salt)
        val invalidToken = "invalid-token"

        // Act & Assert
        assertThrows<BadCredentialsException> {
            resolver.remainingTime(invalidToken)
        }
    }

    @Test
    fun `서명이 일치하지 않는 토큰에 대해 BadCredentialsException 발생한다`() {
        // Arrange
        val otherSaltGenerator = JwtTokenGenerator("other-salt", accessExpiresIn, refreshExpiresIn)
        val tokenFromOtherSalt = otherSaltGenerator.generate(1L, setOf("USER")).accessToken
        val resolver = JwtTokenExpirationResolver(salt)

        // Act & Assert
        assertThrows<BadCredentialsException> {
            resolver.remainingTime(tokenFromOtherSalt)
        }
    }

    @Test
    fun `토큰에 만료 정보가 없는 경우 BadCredentialsException 발생한다`() {
        // Arrange
        val tokenWithoutExpiration = Jwts.builder()
            .subject("1")
            .signWith(Keys.hmacShaKeyFor(("member-token-secret-member-token-secret$salt").toByteArray(java.nio.charset.StandardCharsets.UTF_8)))
            .compact()
        val resolver = JwtTokenExpirationResolver(salt)

        // Act & Assert
        assertThrows<BadCredentialsException> {
            resolver.remainingTime(tokenWithoutExpiration)
        }
    }
}
