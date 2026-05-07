package org.yechan.member

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory
import org.springframework.data.redis.core.StringRedisTemplate
import org.testcontainers.containers.GenericContainer
import java.time.Duration

class AccessTokenBlacklistRedisRepositoryTest {
    private lateinit var redisTemplate: StringRedisTemplate
    private lateinit var repository: AccessTokenBlacklistRedisRepository

    @BeforeEach
    fun setUp() {
        redisTemplate = StringRedisTemplate(connectionFactory)
        redisTemplate.afterPropertiesSet()
        redisTemplate.connectionFactory?.connection?.serverCommands()?.flushAll()
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

        assertThat(repository.contains("access-token")).isFalse()
    }

    companion object {
        private val redis = GenericContainer("redis:7.4-alpine")
            .withExposedPorts(6379)
        private lateinit var connectionFactory: LettuceConnectionFactory

        @BeforeAll
        @JvmStatic
        fun startRedis() {
            redis.start()
            connectionFactory = LettuceConnectionFactory(redis.host, redis.getMappedPort(6379))
            connectionFactory.afterPropertiesSet()
        }

        @AfterAll
        @JvmStatic
        fun stopRedis() {
            connectionFactory.destroy()
            redis.stop()
        }
    }
}
