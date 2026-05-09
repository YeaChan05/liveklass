package org.yechan.course

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import org.yechan.enrollment.EnrollmentModelData
import org.yechan.enrollment.EnrollmentStatus

class EnrollmentModelTest {
    @Test
    fun `수강 신청은 결제 대기 상태로 생성된다`() {
        val enrollment = enrollment()

        assertEquals(10L, enrollment.courseId)
        assertEquals(1L, enrollment.memberId)
        assertEquals(EnrollmentStatus.PENDING, enrollment.status)
    }

    @Test
    fun `결제 대기 신청은 결제로 확정된다`() {
        val confirmed = enrollment().confirmPayment()

        assertEquals(EnrollmentStatus.CONFIRMED, confirmed.status)
    }

    @Test
    fun `확정된 신청은 다시 확정할 수 없다`() {
        val confirmed = enrollment().confirmPayment()

        val exception =
            assertThrows(CourseInvalidStateException::class.java) { confirmed.confirmPayment() }
        assertEquals("결제 대기 상태의 신청만 확정할 수 있습니다.", exception.message)
    }

    @Test
    fun `취소된 신청은 확정할 수 없다`() {
        val cancelled = enrollment().cancel()

        val exception =
            assertThrows(CourseInvalidStateException::class.java) { cancelled.confirmPayment() }
        assertEquals("결제 대기 상태의 신청만 확정할 수 있습니다.", exception.message)
    }

    @Test
    fun `결제 대기 신청은 취소할 수 있다`() {
        val cancelled = enrollment().cancel()

        assertEquals(EnrollmentStatus.CANCELLED, cancelled.status)
    }

    @Test
    fun `확정된 신청은 취소할 수 있다`() {
        val cancelled = enrollment().confirmPayment().cancel()

        assertEquals(EnrollmentStatus.CANCELLED, cancelled.status)
    }

    @Test
    fun `취소된 신청은 다시 취소할 수 없다`() {
        val cancelled = enrollment().cancel()

        val exception = assertThrows(CourseInvalidStateException::class.java) { cancelled.cancel() }
        assertEquals("이미 취소된 신청입니다.", exception.message)
    }

    private fun enrollment() = EnrollmentModelData(
        enrollmentId = 20L,
        courseId = 10L,
        memberId = 1L,
    )
}
