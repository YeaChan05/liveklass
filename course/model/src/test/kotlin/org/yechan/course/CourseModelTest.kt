package org.yechan.course

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import org.yechan.enrollment.EnrollmentStatus
import java.time.LocalDateTime

class CourseModelTest {
    @Test
    fun `강의는 초안 상태로 생성된다`() {
        val course = course()

        assertEquals("Kotlin Basic", course.title)
        assertEquals("Kotlin course", course.description)
        assertEquals(Money(amount = 100000), course.price)
        assertEquals(2, course.capacity)
        assertEquals(LocalDateTime.of(2026, 6, 1, 0, 0), course.periodStart)
        assertEquals(LocalDateTime.of(2026, 6, 30, 0, 0), course.periodEnd)
        assertEquals(CourseStatus.DRAFT, course.status)
    }

    @Test
    fun `초안 강의는 수강 신청을 거부한다`() {
        val course = course()

        val exception = assertThrows(CourseInvalidStateException::class.java) {
            course.requestEnrollment(memberId = 1L, currentEnrollmentCount = 0)
        }
        assertEquals("모집 중인 강의만 마감할 수 있습니다.", exception.message)
    }

    @Test
    fun `모집 중인 강의는 수강 신청을 결제 대기로 받는다`() {
        val course = course().open()

        val enrollment = course.requestEnrollment(memberId = 1L, currentEnrollmentCount = 1)

        assertEquals(10L, enrollment.courseId)
        assertEquals(1L, enrollment.memberId)
        assertEquals(EnrollmentStatus.PENDING, enrollment.status)
    }

    @Test
    fun `초안 강의는 모집 전 마감할 수 없다`() {
        val exception = assertThrows(CourseInvalidStateException::class.java) { course().close() }

        assertEquals("모집 중인 강의만 마감할 수 있습니다.", exception.message)
    }

    @Test
    fun `마감된 강의는 다시 모집할 수 없다`() {
        val closed = course().open().close()

        val exception = assertThrows(CourseInvalidStateException::class.java) { closed.open() }

        assertEquals("초안 상태의 강의만 모집할 수 있습니다.", exception.message)
    }

    @Test
    fun `모집 중인 강의는 정원이 가득 차면 신청을 거부한다`() {
        val course = course().open()

        val exception = assertThrows(CourseInvalidStateException::class.java) {
            course.requestEnrollment(memberId = 1L, currentEnrollmentCount = 2)
        }
        assertEquals("강의 정원을 초과할 수 없습니다.", exception.message)
    }

    @Test
    fun `마감된 강의는 수강 신청을 거부한다`() {
        val course = course().open().close()

        val exception = assertThrows(CourseInvalidStateException::class.java) {
            course.requestEnrollment(memberId = 1L, currentEnrollmentCount = 0)
        }
        assertEquals("모집 중인 강의만 마감할 수 있습니다.", exception.message)
    }

    @Test
    fun `잘못된 수강 기간은 거부된다`() {
        val exception = assertThrows(CourseInvalidStateException::class.java) {
            course(
                periodStart = LocalDateTime.of(2026, 6, 30, 0, 0),
                periodEnd = LocalDateTime.of(2026, 6, 1, 0, 0),
            )
        }
        assertEquals("수강 종료일은 시작일보다 빠를 수 없습니다.", exception.message)
    }

    @Test
    fun `강의 정원은 양수여야 한다`() {
        val exception = assertThrows(CourseInvalidStateException::class.java) { course(capacity = 0) }
        assertEquals("강의 정원은 1명 이상이어야 합니다.", exception.message)
    }

    private fun course(
        capacity: Int = 2,
        periodStart: LocalDateTime = LocalDateTime.of(2026, 6, 1, 0, 0),
        periodEnd: LocalDateTime = LocalDateTime.of(2026, 6, 30, 0, 0),
    ) = CourseModelData(
        courseId = 10L,
        creatorId = 1L,
        title = "Kotlin Basic",
        description = "Kotlin course",
        price = Money(100_000L),
        capacity = capacity,
        periodStart = periodStart,
        periodEnd = periodEnd,
    )
}
