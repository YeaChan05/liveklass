package org.yechan

import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory
import org.springframework.data.redis.core.StringRedisTemplate
import org.testcontainers.containers.GenericContainer

abstract class RedisIntegrationTest {
    companion object {
        private val redis = GenericContainer("redis:7.4-alpine")
            .withExposedPorts(6379)

        @JvmStatic
        protected lateinit var connectionFactory: LettuceConnectionFactory

        @JvmStatic
        protected lateinit var redisTemplate: StringRedisTemplate

        @JvmStatic
        @BeforeAll
        fun startRedis() {
            redis.start()
            connectionFactory = LettuceConnectionFactory(redis.host, redis.getMappedPort(6379))
            connectionFactory.afterPropertiesSet()
            redisTemplate = StringRedisTemplate(connectionFactory)
            redisTemplate.afterPropertiesSet()
        }

        @JvmStatic
        @AfterAll
        fun stopRedis() {
            if (::connectionFactory.isInitialized) {
                connectionFactory.destroy()
            }
            redis.stop()
        }
    }

    protected fun flushAll() {
        redisTemplate.connectionFactory?.connection?.serverCommands()?.flushAll()
    }
}
