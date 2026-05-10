package org.yechan.enrollment

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.time.Clock
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId

class EnrollmentPaymentExpirationSchedulerTest {
    private val useCase = FakeEnrollmentExpirationUseCase()
    private val clock = Clock.fixed(
        Instant.parse("2026-05-10T12:00:00Z"),
        ZoneId.of("Asia/Seoul"),
    )

    private val scheduler =
        EnrollmentPaymentExpirationScheduler(
            enrollmentExpirationUseCase = useCase,
            clock = clock,
        )

    @Test
    fun `스케줄러는 현재 시간을 기준으로 결제 대기 만료 처리를 요청한다`() {
        scheduler.expirePaymentPendingEnrollments()

        assertThat(useCase.calledCount).isEqualTo(1)
        assertThat(useCase.requestedNow).isEqualTo(
            LocalDateTime.of(2026, 5, 10, 21, 0, 0),
        )
    }
}

private class FakeEnrollmentExpirationUseCase : EnrollmentExpirationUseCase {
    var calledCount = 0
    var requestedNow: LocalDateTime? = null

    override fun expirePaymentPendingEnrollments(now: LocalDateTime): Int {
        calledCount++
        requestedNow = now
        return 0
    }
}
