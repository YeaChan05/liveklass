package org.yechan.member

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.yechan.RedisIntegrationTest
import java.time.LocalDateTime

class RefreshTokenRedisRepositoryTest : RedisIntegrationTest() {
    private lateinit var repository: RefreshTokenRedisRepository

    @BeforeEach
    fun setUp() {
        flushAll()
        repository = RefreshTokenRedisRepository(redisTemplate)
    }

    @Test
    fun `리프레시 토큰 저장은 사용자 조회 키도 함께 저장한다`() {
        val refreshToken = refreshToken(userId = 1L, tokenHash = "token-a")

        repository.replace(refreshToken)

        assertThat(repository.findByUserId(1L)).isEqualTo(refreshToken)
        assertThat(repository.findByTokenHash("token-a")).isEqualTo(refreshToken)
    }

    @Test
    fun `리프레시 토큰 교체는 같은 사용자의 이전 토큰을 삭제한다`() {
        repository.replace(refreshToken(userId = 1L, tokenHash = "old-token"))
        val newToken = refreshToken(userId = 1L, tokenHash = "new-token")

        repository.replace(newToken)

        assertThat(repository.findByUserId(1L)).isEqualTo(newToken)
        assertThat(repository.findByTokenHash("old-token")).isNull()
        assertThat(repository.findByTokenHash("new-token")).isEqualTo(newToken)
    }

    @Test
    fun `만료된 리프레시 토큰은 저장하지 않는다`() {
        repository.replace(refreshToken(userId = 1L, tokenHash = "expired", expiresAt = LocalDateTime.now().minusSeconds(1)))

        assertThat(repository.findByUserId(1L)).isNull()
        assertThat(repository.findByTokenHash("expired")).isNull()
    }

    @Test
    fun `사용자 아이디로 삭제하면 해당 사용자의 토큰만 삭제한다`() {
        val first = refreshToken(userId = 1L, tokenHash = "first-token")
        val second = refreshToken(userId = 2L, tokenHash = "second-token")
        repository.replace(first)
        repository.replace(second)

        repository.deleteByUserId(1L)
        repository.deleteByUserId(404L)

        assertThat(repository.findByUserId(1L)).isNull()
        assertThat(repository.findByTokenHash("first-token")).isNull()
        assertThat(repository.findByUserId(2L)).isEqualTo(second)
        assertThat(repository.findByTokenHash("second-token")).isEqualTo(second)
    }

    private fun refreshToken(
        userId: Long,
        tokenHash: String,
        expiresAt: LocalDateTime = LocalDateTime.now().plusMinutes(10),
    ) = RefreshTokenModelData(
        userId = userId,
        tokenHash = tokenHash,
        expiresAt = expiresAt,
    )
}
