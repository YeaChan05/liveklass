package org.yechan.enrollment

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.yechan.RedisIntegrationTest
import java.time.Duration
import java.time.Instant

class EnrollmentWaitlistRedisRepositoryTest : RedisIntegrationTest() {
    private lateinit var repository: EnrollmentWaitlistRedisRepository

    @BeforeEach
    fun setUp() {
        flushAll()
        repository = EnrollmentWaitlistRedisRepository(redisTemplate, Duration.ofDays(1))
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

    @Test
    fun `scheduler가 못 지워도 ttl로 waitlist와 soldOut이 만료된다`() {
        repository = EnrollmentWaitlistRedisRepository(redisTemplate, Duration.ofSeconds(1))

        repository.markSoldOut(1L)
        repository.enqueue(1L, 10L, Instant.parse("2026-01-01T00:00:00Z"))

        assertThat(repository.isSoldOut(1L)).isTrue()
        assertThat(repository.findCourseIds()).containsExactly(1L)

        Thread.sleep(1_200)

        assertThat(repository.findCourseIds()).isEmpty()
        assertThat(repository.isSoldOut(1L)).isFalse()
    }

    @Test
    fun `마지막 대기열이 제거되면 매진 표시도 해제된다`() {
        repository.enqueue(1L, 10L, Instant.parse("2026-01-01T00:00:00Z"))
        repository.enqueue(1L, 20L, Instant.parse("2026-01-01T00:00:01Z"))
        repository.markSoldOut(1L)

        repository.remove(1L, 10L)

        assertThat(repository.isSoldOut(1L)).isTrue()

        repository.remove(1L, 20L)

        assertThat(repository.findCourseIds()).isEmpty()
        assertThat(repository.isSoldOut(1L)).isFalse()
    }

    @Test
    fun `회원 아이디로 대기열을 조회하면 등록 순서대로 반환한다`() {
        repository.enqueue(1L, 10L, Instant.parse("2026-01-01T00:00:00Z"))
        repository.enqueue(2L, 10L, Instant.parse("2026-01-01T00:00:01Z"))
        repository.enqueue(1L, 20L, Instant.parse("2026-01-01T00:00:02Z"))

        val result = repository.findByMemberId(10L)

        assertThat(result).hasSize(2)
        assertThat(result[0].courseId).isEqualTo(1L)
        assertThat(result[0].memberId).isEqualTo(10L)
        assertThat(result[0].requestedAt).isEqualTo(Instant.parse("2026-01-01T00:00:00Z"))
        assertThat(result[1].courseId).isEqualTo(2L)
        assertThat(result[1].memberId).isEqualTo(10L)
        assertThat(result[1].requestedAt).isEqualTo(Instant.parse("2026-01-01T00:00:01Z"))
    }

    @Test
    fun `같은 회원이 같은 강의에 다시 등록하면 기존 대기열을 갱신한다`() {
        repository.enqueue(1L, 10L, Instant.parse("2026-01-01T00:00:00Z"))
        repository.enqueue(1L, 10L, Instant.parse("2026-01-01T00:00:05Z"))

        val result = repository.findByMemberId(10L)

        assertThat(result).hasSize(1)
        assertThat(result.single().requestedAt).isEqualTo(Instant.parse("2026-01-01T00:00:05Z"))
        assertThat(repository.pop(1L)?.requestedAt).isEqualTo(Instant.parse("2026-01-01T00:00:05Z"))
    }

    @Test
    fun `존재하지 않는 대기열을 pop하면 null을 반환한다`() {
        // Arrange
        // do nothing

        // Act
        val result = repository.pop(999L)

        // Assert
        assertThat(result).isNull()
    }

    @Test
    fun `대기열에 여러 강의가 존재하면 모든 courseId를 반환한다`() {
        // Arrange
        repository.enqueue(
            courseId = 1L,
            memberId = 10L,
            requestedAt = Instant.parse("2026-01-01T00:00:00Z"),
        )

        repository.enqueue(
            courseId = 2L,
            memberId = 20L,
            requestedAt = Instant.parse("2026-01-01T00:00:01Z"),
        )

        // Act
        val result = repository.findCourseIds()

        // Assert
        assertThat(result)
            .containsExactlyInAnyOrder(1L, 2L)
    }

    @Test
    fun `존재하지 않는 회원을 제거해도 대기열은 유지된다`() {
        // Arrange
        repository.enqueue(
            courseId = 1L,
            memberId = 10L,
            requestedAt = Instant.parse("2026-01-01T00:00:00Z"),
        )

        // Act
        repository.remove(
            courseId = 1L,
            memberId = 999L,
        )

        // Assert
        assertThat(repository.findCourseIds())
            .containsExactly(1L)

        assertThat(repository.pop(1L)?.memberId)
            .isEqualTo(10L)
    }

    @Test
    fun `대기열에 회원이 남아있으면 courseId는 제거되지 않는다`() {
        // Arrange
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

        // Act
        repository.remove(
            courseId = 1L,
            memberId = 10L,
        )

        // Assert
        assertThat(repository.findCourseIds())
            .containsExactly(1L)

        assertThat(repository.pop(1L)?.memberId)
            .isEqualTo(20L)
    }

    @Test
    fun `마지막 대기 회원이 제거되면 courseId도 제거된다`() {
        // Arrange
        repository.enqueue(
            courseId = 1L,
            memberId = 10L,
            requestedAt = Instant.parse("2026-01-01T00:00:00Z"),
        )

        // Act
        repository.remove(
            courseId = 1L,
            memberId = 10L,
        )

        // Assert
        assertThat(repository.findCourseIds()).isEmpty()

        assertThat(repository.pop(1L)).isNull()
    }

    @Test
    fun `마지막 대기 회원을 pop하면 courseId도 제거된다`() {
        // Arrange
        repository.enqueue(
            courseId = 1L,
            memberId = 10L,
            requestedAt = Instant.parse("2026-01-01T00:00:00Z"),
        )
        repository.markSoldOut(1L)

        // Act
        repository.pop(1L)

        // Assert
        assertThat(repository.findCourseIds()).isEmpty()
        assertThat(repository.isSoldOut(1L)).isFalse()
    }

    @Test
    fun `courseId 목록에 숫자가 아닌 값이 있으면 무시한다`() {
        // Arrange
        redisTemplate.opsForZSet().add(
            "course:enrollment:waitlist:courses",
            "invalid-course-id",
            1.0,
        )

        repository.enqueue(
            courseId = 1L,
            memberId = 10L,
            requestedAt = Instant.parse("2026-01-01T00:00:00Z"),
        )

        // Act
        val result = repository.findCourseIds()

        // Assert
        assertThat(result).containsExactly(1L)
    }

    @Test
    fun `같은 timestamp로 등록된 대기열은 memberId 오름차순으로 꺼낸다`() {
        // Arrange
        val requestedAt = Instant.parse("2026-01-01T00:00:00Z")

        repository.enqueue(
            courseId = 1L,
            memberId = 20L,
            requestedAt = requestedAt,
        )

        repository.enqueue(
            courseId = 1L,
            memberId = 10L,
            requestedAt = requestedAt,
        )

        // Act
        val first = repository.pop(1L)
        val second = repository.pop(1L)

        // Assert
        assertThat(first?.memberId).isEqualTo(10L)
        assertThat(second?.memberId).isEqualTo(20L)
    }

    @Test
    fun `한 강의의 대기열이 비어도 다른 강의의 courseId는 유지된다`() {
        // Arrange
        repository.enqueue(
            courseId = 1L,
            memberId = 10L,
            requestedAt = Instant.parse("2026-01-01T00:00:00Z"),
        )

        repository.enqueue(
            courseId = 2L,
            memberId = 20L,
            requestedAt = Instant.parse("2026-01-01T00:00:01Z"),
        )

        // Act
        repository.pop(1L)

        // Assert
        assertThat(repository.findCourseIds()).containsExactly(2L)
        assertThat(repository.pop(2L)?.memberId).isEqualTo(20L)
    }

    @Test
    fun `같은 회원이 여러 번 등록되어 있으면 remove는 해당 회원의 대기열을 모두 제거한다`() {
        // Arrange
        repository.enqueue(
            courseId = 1L,
            memberId = 10L,
            requestedAt = Instant.parse("2026-01-01T00:00:00Z"),
        )

        repository.enqueue(
            courseId = 1L,
            memberId = 10L,
            requestedAt = Instant.parse("2026-01-01T00:00:01Z"),
        )

        repository.enqueue(
            courseId = 1L,
            memberId = 20L,
            requestedAt = Instant.parse("2026-01-01T00:00:02Z"),
        )

        // Act
        repository.remove(
            courseId = 1L,
            memberId = 10L,
        )

        // Assert
        assertThat(repository.pop(1L)?.memberId).isEqualTo(20L)
        assertThat(repository.pop(1L)).isNull()
    }

    @Test
    fun `잘못된 대기열 값이 저장되어 있으면 pop은 null을 반환하고 정리한다`() {
        // Arrange
        redisTemplate.opsForZSet().add(
            "course:enrollment:waitlist:course:1",
            "invalid-value",
            1.0,
        )

        redisTemplate.opsForZSet().add(
            "course:enrollment:waitlist:courses",
            "1",
            1.0,
        )

        // Act
        val result = repository.pop(1L)

        // Assert
        assertThat(result).isNull()
        assertThat(repository.findCourseIds()).isEmpty()
    }

    @Test
    fun `대기열에서 제거된 강의의 수강신청은 조회되지 않는다`() {
        // Arrange
        val courseId = 100L

        redisTemplate.opsForValue().set(
            "course:enrollment:waitlist:course:$courseId:sold-out",
            "true",
        )

        // Act
        repository.clearSoldOut(courseId)

        // Assert
        assertThat(redisTemplate.hasKey("course:enrollment:waitlist:course:$courseId:sold-out")).isFalse()
    }
}
