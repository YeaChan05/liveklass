package org.yechan.auth

import io.jsonwebtoken.Jwts
import io.jsonwebtoken.security.Keys
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.springframework.security.authentication.BadCredentialsException
import java.nio.charset.StandardCharsets
import javax.crypto.SecretKey

class JwtTokenVerifierTest {

    private val salt = "test-salt"
    private val verifier = JwtTokenVerifier(salt)

    @Test
    fun `유효한 토큰은 Authentication으로 변환된다`() {
        // Arrange
        val token = jwtToken(
            subject = "1",
            roles = listOf("USER", "ADMIN"),
        )

        // Act
        val authentication = verifier.verify(token)

        // Assert
        assertThat(authentication.principal).isEqualTo("1")

        assertThat(authentication.authorities)
            .extracting("authority")
            .containsExactlyInAnyOrder(
                "ROLE_USER",
                "ROLE_ADMIN",
            )
    }

    @Test
    fun `roles claim이 없으면 빈 권한으로 처리한다`() {
        // Arrange
        val token = jwtToken(
            subject = "1",
        )

        // Act
        val authentication = verifier.verify(token)

        // Assert
        assertThat(authentication.authorities).isEmpty()
    }

    @Test
    fun `subject가 없으면 예외가 발생한다`() {
        // Arrange
        val token = Jwts.builder()
            .claim("roles", listOf("USER"))
            .signWith(secretKey())
            .compact()

        // Act & Assert
        assertThatThrownBy {
            verifier.verify(token)
        }
            .isInstanceOf(BadCredentialsException::class.java)
            .hasMessage("Invalid token subject")
    }

    @Test
    fun `서명이 올바르지 않은 토큰은 예외가 발생한다`() {
        // Arrange
        val invalidToken = Jwts.builder()
            .subject("1")
            .claim("roles", listOf("USER"))
            .signWith(
                Keys.hmacShaKeyFor(
                    "another-secret-another-secret-another-secret"
                        .toByteArray(StandardCharsets.UTF_8),
                ),
            )
            .compact()

        // Act & Assert
        assertThatThrownBy {
            verifier.verify(invalidToken)
        }
            .isInstanceOf(BadCredentialsException::class.java)
            .hasMessage("Invalid token")
    }

    @Test
    fun `형식이 잘못된 토큰은 예외가 발생한다`() {
        // Arrange
        val invalidToken = "invalid-token"

        // Act & Assert
        assertThatThrownBy {
            verifier.verify(invalidToken)
        }
            .isInstanceOf(BadCredentialsException::class.java)
            .hasMessage("Invalid token")
    }

    @Test
    fun `roles claim에 문자열이 아닌 값이 있으면 무시한다`() {
        // Arrange
        val token = Jwts.builder()
            .subject("1")
            .claim("roles", listOf("USER", 123, true))
            .signWith(secretKey())
            .compact()

        // Act
        val authentication = verifier.verify(token)

        // Assert
        assertThat(authentication.authorities)
            .extracting("authority")
            .containsExactly("ROLE_USER")
    }

    private fun jwtToken(
        subject: String,
        roles: List<String>? = null,
    ): String {
        val builder = Jwts.builder()
            .subject(subject)

        roles?.let {
            builder.claim("roles", it)
        }

        return builder
            .signWith(secretKey())
            .compact()
    }

    private fun secretKey(): SecretKey {
        val secret =
            "member-token-secret-member-token-secret$salt"

        return Keys.hmacShaKeyFor(
            secret.toByteArray(StandardCharsets.UTF_8),
        )
    }
}
