package org.yechan.member

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.yechan.RedisIntegrationTest
import java.time.Duration

class AccessTokenBlacklistRedisRepositoryTest : RedisIntegrationTest() {
    private lateinit var repository: AccessTokenBlacklistRedisRepository

    @BeforeEach
    fun setUp() {
        flushAll()
        repository = AccessTokenBlacklistRedisRepository(redisTemplate)
    }

    @Test
    fun `액세스 토큰을 TTL과 함께 블랙리스트에 등록한다`() {
        repository.blacklist("access-token", Duration.ofMinutes(10))

        assertThat(repository.contains("access-token")).isTrue()
        assertThat(repository.contains("other-token")).isFalse()
    }

    @Test
    fun `만료된 TTL은 블랙리스트에 등록하지 않는다`() {
        repository.blacklist("access-token", Duration.ZERO)
        repository.blacklist("access-token-negative", Duration.ofSeconds(-1))

        assertThat(repository.contains("access-token")).isFalse()
        assertThat(repository.contains("access-token-negative")).isFalse()
    }
}
