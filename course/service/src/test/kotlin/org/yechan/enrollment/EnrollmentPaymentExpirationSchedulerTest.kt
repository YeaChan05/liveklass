package org.yechan.enrollment

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.yechan.FakeEnrollmentRepository
import org.yechan.FakeEnrollmentWaitlistRepository
import java.time.Clock
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneOffset

class EnrollmentPaymentExpirationSchedulerTest {
    private val now = LocalDateTime.of(2026, 1, 1, 12, 0)
    private val clock = Clock.fixed(Instant.parse("2026-01-01T12:00:00Z"), ZoneOffset.UTC)
    private val enrollmentRepository = FakeEnrollmentRepository()
    private val waitlistRepository = FakeEnrollmentWaitlistRepository()

    @Test
    fun `만료 대상이 있으면 processor를 호출하고 sold out 상태를 해제한다`() {
        givenPendingEnrollment(
            enrollmentId = 1L,
            courseId = 1L,
            memberId = 1L,
            paymentPendingExpiresAt = now.minusMinutes(1),
        )
        givenPendingEnrollment(
            enrollmentId = 2L,
            courseId = 1L,
            memberId = 2L,
            paymentPendingExpiresAt = now.minusMinutes(2),
        )
        givenPendingEnrollment(
            enrollmentId = 3L,
            courseId = 2L,
            memberId = 3L,
            paymentPendingExpiresAt = now.minusMinutes(1),
        )

        waitlistRepository.markSoldOut(1L)
        waitlistRepository.markSoldOut(2L)

        val processor = RecordingProcessor(
            result = mapOf(
                1L to 2,
                2L to 1,
            ),
        )
        val scheduler = EnrollmentPaymentExpirationScheduler(
            enrollmentRepository = enrollmentRepository,
            enrollmentExpirationProcessor = processor,
            waitlistRepository = waitlistRepository,
            clock = clock,
        )

        scheduler.expirePaymentPendingEnrollments()

        assertThat(processor.lastCourseIds).containsExactlyInAnyOrder(1L, 2L)
        assertThat(processor.lastNow).isEqualTo(now)
        assertThat(waitlistRepository.isSoldOut(1L)).isFalse()
        assertThat(waitlistRepository.isSoldOut(2L)).isFalse()
    }

    @Test
    fun `만료 대상이 없으면 processor를 호출하지 않는다`() {
        val processor = RecordingProcessor(result = emptyMap())
        val scheduler = EnrollmentPaymentExpirationScheduler(
            enrollmentRepository = enrollmentRepository,
            enrollmentExpirationProcessor = processor,
            waitlistRepository = waitlistRepository,
            clock = clock,
        )

        scheduler.expirePaymentPendingEnrollments()

        assertThat(processor.called).isFalse()
    }

    private fun givenPendingEnrollment(
        enrollmentId: Long,
        courseId: Long,
        memberId: Long,
        paymentPendingExpiresAt: LocalDateTime,
    ) {
        enrollmentRepository.save(
            EnrollmentModelData(
                enrollmentId = enrollmentId,
                courseId = courseId,
                memberId = memberId,
                status = EnrollmentStatus.PENDING,
                paymentPendingStartedAt = paymentPendingExpiresAt.minusMinutes(10),
                paymentPendingExpiresAt = paymentPendingExpiresAt,
            ),
        )
    }

    private class RecordingProcessor(
        private val result: Map<Long, Int>,
    ) : EnrollmentExpirationProcessor {
        var called = false
            private set
        var lastCourseIds: Collection<Long> = emptyList()
            private set
        var lastNow: LocalDateTime? = null
            private set

        override fun expireAll(
            courseIds: Collection<Long>,
            now: LocalDateTime,
        ): Map<Long, Int> {
            called = true
            lastCourseIds = courseIds.toList()
            lastNow = now
            return result
        }
    }
}
