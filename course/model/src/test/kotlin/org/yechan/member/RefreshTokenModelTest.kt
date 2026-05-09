package org.yechan.member

import org.assertj.core.api.Assertions.assertThatCode
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import java.time.LocalDateTime

class RefreshTokenModelTest {

    @Test
    fun `만료된 토큰 검증 시 예외가 발생한다`() {
        val expiredToken = RefreshTokenModelData(
            userId = 1L,
            tokenHash = "hash",
            expiresAt = LocalDateTime.now().minusSeconds(1),
        )

        assertThatThrownBy { verifyTokenExpiry(expiredToken) }
            .isInstanceOf(InvalidRefreshTokenException::class.java)
    }

    @Test
    fun `만료되지 않은 토큰 검증 시 예외가 발생하지 않는다`() {
        val validToken = RefreshTokenModelData(
            userId = 1L,
            tokenHash = "hash",
            expiresAt = LocalDateTime.now().plusHours(1),
        )

        assertThatCode { verifyTokenExpiry(validToken) }
            .doesNotThrowAnyException()
    }

    @Test
    fun `사용자 ID가 일치하지 않으면 예외가 발생한다`() {
        val token = RefreshTokenModelData(
            userId = 1L,
            tokenHash = "hash",
            expiresAt = LocalDateTime.now().plusHours(1),
        )

        assertThatThrownBy { validateUserIdMatch(token, 2L) }
            .isInstanceOf(InvalidRefreshTokenException::class.java)
    }

    @Test
    fun `사용자 ID가 일치하면 예외가 발생하지 않는다`() {
        val token = RefreshTokenModelData(
            userId = 1L,
            tokenHash = "hash",
            expiresAt = LocalDateTime.now().plusHours(1),
        )

        assertThatCode { validateUserIdMatch(token, 1L) }
            .doesNotThrowAnyException()
    }
}
