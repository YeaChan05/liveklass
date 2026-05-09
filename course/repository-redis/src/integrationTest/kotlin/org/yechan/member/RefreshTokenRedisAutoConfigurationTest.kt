package org.yechan.member

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.SpringBootConfiguration
import org.springframework.boot.autoconfigure.EnableAutoConfiguration
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.testcontainers.containers.GenericContainer
import java.time.LocalDateTime

@SpringBootTest(classes = [RefreshTokenRedisAutoConfigurationTest.TestApplication::class])
class RefreshTokenRedisAutoConfigurationTest {
    @Autowired
    private lateinit var refreshTokenRepository: RefreshTokenRepository

    @Test
    fun `자동 설정은 리프레시 토큰 저장소 빈을 등록한다`() {
        val refreshToken = RefreshTokenModelData(
            userId = 1L,
            tokenHash = "token-hash",
            expiresAt = LocalDateTime.now().plusMinutes(10),
        )

        refreshTokenRepository.replace(refreshToken)

        assertThat(refreshTokenRepository).isInstanceOf(RefreshTokenRedisRepository::class.java)
        assertThat(refreshTokenRepository.findByUserId(1L)).isEqualTo(refreshToken)
    }

    @SpringBootConfiguration
    @EnableAutoConfiguration
    class TestApplication

    companion object {
        private val redis = GenericContainer("redis:7.4-alpine")
            .withExposedPorts(6379)

        init {
            redis.start()
        }

        @JvmStatic
        @DynamicPropertySource
        fun redisProperties(registry: DynamicPropertyRegistry) {
            registry.add("spring.data.redis.host", redis::getHost)
            registry.add("spring.data.redis.port") { redis.getMappedPort(6379) }
        }

        @JvmStatic
        @AfterAll
        fun stopRedis() {
            redis.stop()
        }
    }
}
