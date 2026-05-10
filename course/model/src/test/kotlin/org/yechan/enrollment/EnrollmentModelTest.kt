package org.yechan.enrollment

import org.assertj.core.api.Assertions
import org.junit.jupiter.api.Test
import org.yechan.course.CourseInvalidStateException
import java.time.LocalDateTime

class EnrollmentModelTest {
    @Test
    fun `수강 신청은 기본적으로 결제 대기 상태로 생성된다`() {
        val enrollment = enrollment()

        Assertions.assertThat(enrollment.status).isEqualTo(EnrollmentStatus.PENDING)
        Assertions.assertThat(enrollment.courseId).isEqualTo(1L)
        Assertions.assertThat(enrollment.memberId).isEqualTo(2L)
    }

    @Test
    fun `결제 대기 신청은 결제 확정할 수 있다`() {
        val enrollment = enrollment()

        val confirmed = enrollment.confirm(now = beforeExpiredAt())

        Assertions.assertThat(confirmed.status).isEqualTo(EnrollmentStatus.CONFIRMED)
    }

    @Test
    fun `confirmPayment는 결제 대기 신청을 결제 확정 상태로 변경한다`() {
        val enrollment = enrollment()

        val confirmed = enrollment.confirmPayment(now = beforeExpiredAt())

        Assertions.assertThat(confirmed.status).isEqualTo(EnrollmentStatus.CONFIRMED)
    }

    @Test
    fun `결제 대기 시간이 만료된 신청은 결제 확정할 수 없다`() {
        val enrollment = enrollment()

        Assertions.assertThatThrownBy {
            enrollment.confirm(now = expiredAt())
        }
            .isInstanceOf(CourseInvalidStateException::class.java)
            .hasMessage("결제 대기 시간이 만료되었습니다.")

        Assertions.assertThat(enrollment.status).isEqualTo(EnrollmentStatus.PENDING)
    }

    @Test
    fun `결제 대기 상태가 아닌 신청은 결제 확정할 수 없다`() {
        listOf(
            EnrollmentStatus.CONFIRMED,
            EnrollmentStatus.CANCELLED,
            EnrollmentStatus.EXPIRED,
        ).forEach { status ->
            val enrollment = enrollment(status = status)

            Assertions.assertThatThrownBy {
                enrollment.confirm(now = beforeExpiredAt())
            }
                .isInstanceOf(CourseInvalidStateException::class.java)
                .hasMessage("결제 대기 상태의 신청만 확정할 수 있습니다.")

            Assertions.assertThat(enrollment.status).isEqualTo(status)
        }
    }

    @Test
    fun `결제 대기 신청은 취소할 수 있다`() {
        val enrollment = enrollment()

        val cancelled = enrollment.cancel()

        Assertions.assertThat(cancelled.status).isEqualTo(EnrollmentStatus.CANCELLED)
    }

    @Test
    fun `결제 대기 상태가 아닌 신청은 취소할 수 없다`() {
        listOf(
            EnrollmentStatus.CONFIRMED,
            EnrollmentStatus.CANCELLED,
            EnrollmentStatus.EXPIRED,
        ).forEach { status ->
            val enrollment = enrollment(status = status)

            Assertions.assertThatThrownBy {
                enrollment.cancel()
            }
                .isInstanceOf(CourseInvalidStateException::class.java)
                .hasMessage("결제 대기 상태에서만 취소가 가능합니다.")

            Assertions.assertThat(enrollment.status).isEqualTo(status)
        }
    }

    @Test
    fun `결제 대기 시간이 만료된 신청은 만료 처리할 수 있다`() {
        val enrollment = enrollment()

        val expired = enrollment.expirePaymentPending(now = expiredAt())

        Assertions.assertThat(expired.status).isEqualTo(EnrollmentStatus.EXPIRED)
    }

    @Test
    fun `결제 대기 시간이 지나지 않은 신청은 만료 처리할 수 없다`() {
        val enrollment = enrollment()

        Assertions.assertThatThrownBy {
            enrollment.expirePaymentPending(now = beforeExpiredAt())
        }
            .isInstanceOf(CourseInvalidStateException::class.java)
            .hasMessage("결제 대기 시간이 아직 만료되지 않았습니다.")

        Assertions.assertThat(enrollment.status).isEqualTo(EnrollmentStatus.PENDING)
    }

    @Test
    fun `결제 대기 상태가 아닌 신청은 만료 처리할 수 없다`() {
        listOf(
            EnrollmentStatus.CONFIRMED,
            EnrollmentStatus.CANCELLED,
            EnrollmentStatus.EXPIRED,
        ).forEach { status ->
            val enrollment = enrollment(status = status)

            Assertions.assertThatThrownBy {
                enrollment.expirePaymentPending(now = expiredAt())
            }
                .isInstanceOf(CourseInvalidStateException::class.java)
                .hasMessage("결제 대기 상태의 신청만 만료 처리할 수 있습니다.")

            Assertions.assertThat(enrollment.status).isEqualTo(status)
        }
    }

    @Test
    fun `결제 대기 만료 시각 이전이면 만료되지 않은 상태다`() {
        val enrollment = enrollment()

        val expired = enrollment.isPaymentPendingExpired(now = beforeExpiredAt())

        Assertions.assertThat(expired).isFalse()
    }

    @Test
    fun `결제 대기 만료 시각과 같으면 만료된 상태다`() {
        val enrollment = enrollment()

        val expired = enrollment.isPaymentPendingExpired(now = expiredAt())

        Assertions.assertThat(expired).isTrue()
    }

    @Test
    fun `결제 대기 만료 시각 이후이면 만료된 상태다`() {
        val enrollment = enrollment()

        val expired = enrollment.isPaymentPendingExpired(now = afterExpiredAt())

        Assertions.assertThat(expired).isTrue()
    }

    @Test
    fun `결제 대기 상태가 아니면 결제 대기 만료 대상으로 보지 않는다`() {
        listOf(
            EnrollmentStatus.CONFIRMED,
            EnrollmentStatus.CANCELLED,
            EnrollmentStatus.EXPIRED,
        ).forEach { status ->
            val enrollment = enrollment(status = status)

            val expired = enrollment.isPaymentPendingExpired(now = afterExpiredAt())

            Assertions.assertThat(expired).isFalse()
        }
    }

    @Test
    fun `좌석 점유 상태를 판단할 수 있다`() {
        Assertions.assertThat(enrollment(status = EnrollmentStatus.PENDING).isSeatOccupied()).isTrue()
        Assertions.assertThat(enrollment(status = EnrollmentStatus.CONFIRMED).isSeatOccupied()).isTrue()
        Assertions.assertThat(enrollment(status = EnrollmentStatus.CANCELLED).isSeatOccupied()).isFalse()
        Assertions.assertThat(enrollment(status = EnrollmentStatus.EXPIRED).isSeatOccupied()).isFalse()
    }

    @Test
    fun `결제 대기 만료 시각은 결제 대기 시작 시각보다 이후여야 한다`() {
        Assertions.assertThatThrownBy {
            EnrollmentModelData(
                courseId = 1L,
                memberId = 2L,
                paymentPendingStartedAt = startedAt(),
                paymentPendingExpiresAt = startedAt(),
            )
        }
            .isInstanceOf(IllegalArgumentException::class.java)
            .hasMessage("결제 대기 만료 시각은 결제 대기 시작 시각보다 이후여야 합니다.")
    }

    private fun enrollment(
        status: EnrollmentStatus = EnrollmentStatus.PENDING,
    ): EnrollmentModelData = EnrollmentModelData(
        enrollmentId = 1L,
        courseId = 1L,
        memberId = 2L,
        status = status,
        paymentPendingStartedAt = startedAt(),
        paymentPendingExpiresAt = expiredAt(),
    )

    private fun startedAt(): LocalDateTime = LocalDateTime.of(2026, 6, 1, 10, 0, 0)

    private fun beforeExpiredAt(): LocalDateTime = LocalDateTime.of(2026, 6, 1, 10, 9, 59)

    private fun expiredAt(): LocalDateTime = LocalDateTime.of(2026, 6, 1, 10, 10, 0)

    private fun afterExpiredAt(): LocalDateTime = LocalDateTime.of(2026, 6, 1, 10, 10, 1)
}
