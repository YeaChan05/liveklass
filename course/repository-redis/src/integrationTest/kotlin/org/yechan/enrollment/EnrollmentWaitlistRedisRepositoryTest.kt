package org.yechan.enrollment

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.yechan.RedisIntegrationTest
import java.time.Instant

class EnrollmentWaitlistRedisRepositoryTest : RedisIntegrationTest() {
    private lateinit var repository: EnrollmentWaitlistRedisRepository

    @BeforeEach
    fun setUp() {
        flushAll()
        repository = EnrollmentWaitlistRedisRepository(redisTemplate)
    }

    @Test
    fun `대기열은 memberId와 timestamp 순으로 저장되고 가장 오래된 순서로 꺼낸다`() {
        repository.enqueue(
            courseId = 1L,
            memberId = 10L,
            requestedAt = Instant.parse("2026-01-01T00:00:00Z"),
        )
        repository.enqueue(
            courseId = 1L,
            memberId = 20L,
            requestedAt = Instant.parse("2026-01-01T00:00:01Z"),
        )

        assertThat(repository.findCourseIds()).containsExactly(1L)

        val first = repository.pop(1L)
        val second = repository.pop(1L)

        assertThat(first?.memberId).isEqualTo(10L)
        assertThat(first?.requestedAt).isEqualTo(Instant.parse("2026-01-01T00:00:00Z"))
        assertThat(second?.memberId).isEqualTo(20L)
        assertThat(second?.requestedAt).isEqualTo(Instant.parse("2026-01-01T00:00:01Z"))
        assertThat(repository.findCourseIds()).isEmpty()
    }

    @Test
    fun `대기열에서 특정 회원을 제거할 수 있다`() {
        repository.enqueue(1L, 10L, Instant.parse("2026-01-01T00:00:00Z"))
        repository.enqueue(1L, 20L, Instant.parse("2026-01-01T00:00:01Z"))

        repository.remove(1L, 10L)

        assertThat(repository.pop(1L)?.memberId).isEqualTo(20L)
    }
}
