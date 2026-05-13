package org.yechan.enrollment

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.yechan.FakeCourseRepository
import org.yechan.FakeEnrollmentRepository
import org.yechan.FakeEnrollmentWaitlistRepository
import java.time.Clock
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId

class EnrollmentPaymentExpirationSchedulerTest {
    private val clock = Clock.fixed(
        Instant.parse("2026-05-10T12:00:00Z"),
        ZoneId.of("Asia/Seoul"),
    )

    val enrollmentRepository = FakeEnrollmentRepository()
    private val scheduler =
        EnrollmentPaymentExpirationScheduler(
            enrollmentRepository = enrollmentRepository,
            courseRepository = FakeCourseRepository(),
            waitlistRepository = FakeEnrollmentWaitlistRepository(),
            clock = clock,
        )

    @Test
    fun `스케줄러는 현재 시간을 기준으로 결제 대기 만료 처리를 요청한다`() {
        scheduler.expirePaymentPendingEnrollments()

        val expiredTargets = enrollmentRepository.findExpiredPaymentPendingTargets(
            now = LocalDateTime.of(2026, 5, 10, 21, 0, 0),
            limit = 100,
        )

        assertThat(expiredTargets).hasSize(0)
    }
}
