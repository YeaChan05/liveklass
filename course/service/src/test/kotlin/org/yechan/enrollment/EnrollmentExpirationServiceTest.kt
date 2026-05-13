package org.yechan.enrollment

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.yechan.FakeCourseRepository
import org.yechan.FakeEnrollmentRepository
import org.yechan.course.CourseInvalidStateException
import org.yechan.course.CourseModelData
import org.yechan.course.CourseStatus
import org.yechan.course.Money
import java.time.LocalDateTime

class EnrollmentExpirationServiceTest {
    private val now = LocalDateTime.of(2026, 1, 1, 12, 0)

    private val enrollmentRepository = FakeEnrollmentRepository()
    private val courseRepository = FakeCourseRepository()

    private val service = EnrollmentExpirationService(
        enrollmentBulkWriter = enrollmentRepository,
        courseBulkWriter = courseRepository,
    )

    @Test
    fun `만료 대상이 없으면 아무 것도 처리하지 않고 빈 결과를 반환한다`() {
        givenCourse(courseId = 1L, seatLeftCount = 1, capacity = 2)
        givenPendingEnrollment(
            enrollmentId = 1L,
            courseId = 1L,
            memberId = 1L,
            paymentPendingExpiresAt = now.plusMinutes(1),
        )

        val expiredCounts = service.expireAll(courseIds = listOf(1L), now = now)

        assertThat(expiredCounts).isEmpty()
        assertThat(enrollmentRepository.findById(1L)?.status).isEqualTo(EnrollmentStatus.PENDING)
        assertThat(courseRepository.findById(1L)?.seatLeftCount).isEqualTo(1)
    }

    @Test
    fun `만료된 결제 대기 신청을 EXPIRED로 변경하고 좌석을 반환한다`() {
        givenCourse(courseId = 1L, seatLeftCount = 1, capacity = 2)

        givenPendingEnrollment(
            enrollmentId = 1L,
            courseId = 1L,
            memberId = 1L,
            paymentPendingExpiresAt = now.minusSeconds(1),
        )

        val expiredCounts = service.expireAll(courseIds = listOf(1L), now = now)

        assertThat(expiredCounts).isEqualTo(mapOf(1L to 1))
        assertThat(enrollmentRepository.findById(1L)?.status).isEqualTo(EnrollmentStatus.EXPIRED)
        assertThat(courseRepository.findById(1L)?.seatLeftCount).isEqualTo(2)
    }

    @Test
    fun `결제 대기 만료 시각이 현재 시각과 같으면 만료 처리한다`() {
        givenCourse(courseId = 1L, seatLeftCount = 1, capacity = 2)
        givenPendingEnrollment(
            enrollmentId = 1L,
            courseId = 1L,
            memberId = 1L,
            paymentPendingExpiresAt = now,
        )

        val expiredCounts = service.expireAll(courseIds = listOf(1L), now = now)

        assertThat(expiredCounts).isEqualTo(mapOf(1L to 1))
        assertThat(enrollmentRepository.findById(1L)?.status).isEqualTo(EnrollmentStatus.EXPIRED)
        assertThat(courseRepository.findById(1L)?.seatLeftCount).isEqualTo(2)
    }

    @Test
    fun `만료되지 않은 결제 대기 신청은 처리하지 않는다`() {
        givenCourse(courseId = 1L, seatLeftCount = 1, capacity = 2)
        givenPendingEnrollment(
            enrollmentId = 1L,
            courseId = 1L,
            memberId = 1L,
            paymentPendingExpiresAt = now.plusSeconds(1),
        )

        val expiredCounts = service.expireAll(courseIds = listOf(1L), now = now)

        assertThat(expiredCounts).isEmpty()
        assertThat(enrollmentRepository.findById(1L)?.status).isEqualTo(EnrollmentStatus.PENDING)
        assertThat(courseRepository.findById(1L)?.seatLeftCount).isEqualTo(1)
    }

    @Test
    fun `이미 확정된 신청은 만료 대상에서 제외한다`() {
        givenCourse(courseId = 1L, seatLeftCount = 1, capacity = 2)
        givenEnrollment(
            enrollmentId = 1L,
            courseId = 1L,
            memberId = 1L,
            status = EnrollmentStatus.CONFIRMED,
            paymentPendingExpiresAt = now.minusMinutes(1),
        )

        val expiredCounts = service.expireAll(courseIds = listOf(1L), now = now)

        assertThat(expiredCounts).isEmpty()
        assertThat(enrollmentRepository.findById(1L)?.status).isEqualTo(EnrollmentStatus.CONFIRMED)
        assertThat(courseRepository.findById(1L)?.seatLeftCount).isEqualTo(1)
    }

    @Test
    fun `이미 취소된 신청은 만료 대상에서 제외한다`() {
        givenCourse(courseId = 1L, seatLeftCount = 1, capacity = 2)
        givenEnrollment(
            enrollmentId = 1L,
            courseId = 1L,
            memberId = 1L,
            status = EnrollmentStatus.CANCELLED,
            paymentPendingExpiresAt = now.minusMinutes(1),
        )

        val expiredCounts = service.expireAll(courseIds = listOf(1L), now = now)

        assertThat(expiredCounts).isEmpty()
        assertThat(enrollmentRepository.findById(1L)?.status).isEqualTo(EnrollmentStatus.CANCELLED)
        assertThat(courseRepository.findById(1L)?.seatLeftCount).isEqualTo(1)
    }

    @Test
    fun `이미 만료된 신청은 다시 만료 처리하지 않는다`() {
        givenCourse(courseId = 1L, seatLeftCount = 1, capacity = 2)
        givenEnrollment(
            enrollmentId = 1L,
            courseId = 1L,
            memberId = 1L,
            status = EnrollmentStatus.EXPIRED,
            paymentPendingExpiresAt = now.minusMinutes(1),
        )

        val expiredCounts = service.expireAll(courseIds = listOf(1L), now = now)

        assertThat(expiredCounts).isEmpty()
        assertThat(enrollmentRepository.findById(1L)?.status).isEqualTo(EnrollmentStatus.EXPIRED)
        assertThat(courseRepository.findById(1L)?.seatLeftCount).isEqualTo(1)
    }

    @Test
    fun `여러 만료 대상은 모두 처리하고 course별 개수를 반환한다`() {
        givenCourse(courseId = 1L, seatLeftCount = 0, capacity = 2)
        givenCourse(courseId = 2L, seatLeftCount = 1, capacity = 3)

        givenPendingEnrollment(
            enrollmentId = 1L,
            courseId = 1L,
            memberId = 1L,
            paymentPendingExpiresAt = now.minusMinutes(2),
        )
        givenPendingEnrollment(
            enrollmentId = 2L,
            courseId = 1L,
            memberId = 2L,
            paymentPendingExpiresAt = now.minusMinutes(1),
        )
        givenPendingEnrollment(
            enrollmentId = 3L,
            courseId = 2L,
            memberId = 3L,
            paymentPendingExpiresAt = now.minusMinutes(1),
        )

        val expiredCounts = service.expireAll(courseIds = listOf(1L, 2L), now = now)

        assertThat(expiredCounts).isEqualTo(mapOf(1L to 2, 2L to 1))
        assertThat(enrollmentRepository.findById(1L)?.status).isEqualTo(EnrollmentStatus.EXPIRED)
        assertThat(enrollmentRepository.findById(2L)?.status).isEqualTo(EnrollmentStatus.EXPIRED)
        assertThat(enrollmentRepository.findById(3L)?.status).isEqualTo(EnrollmentStatus.EXPIRED)

        assertThat(courseRepository.findById(1L)?.seatLeftCount).isEqualTo(2)
        assertThat(courseRepository.findById(2L)?.seatLeftCount).isEqualTo(2)
    }

    @Test
    fun `만료 대상은 지정된 courseIds에 대해서만 처리한다`() {
        givenCourse(courseId = 1L, seatLeftCount = 0, capacity = 2)
        givenCourse(courseId = 2L, seatLeftCount = 1, capacity = 3)

        givenPendingEnrollment(
            enrollmentId = 1L,
            courseId = 1L,
            memberId = 1L,
            paymentPendingExpiresAt = now.minusMinutes(1),
        )
        givenPendingEnrollment(
            enrollmentId = 2L,
            courseId = 2L,
            memberId = 2L,
            paymentPendingExpiresAt = now.minusMinutes(1),
        )

        val expiredCounts = service.expireAll(courseIds = listOf(1L), now = now)

        assertThat(expiredCounts).isEqualTo(mapOf(1L to 1))
        assertThat(enrollmentRepository.findById(1L)?.status).isEqualTo(EnrollmentStatus.EXPIRED)
        assertThat(enrollmentRepository.findById(2L)?.status).isEqualTo(EnrollmentStatus.PENDING)
        assertThat(courseRepository.findById(1L)?.seatLeftCount).isEqualTo(1)
        assertThat(courseRepository.findById(2L)?.seatLeftCount).isEqualTo(1)
    }

    @Test
    fun `좌석 반환이 capacity를 넘으면 예외가 발생한다`() {
        givenCourse(courseId = 1L, seatLeftCount = 2, capacity = 2)
        givenPendingEnrollment(
            enrollmentId = 1L,
            courseId = 1L,
            memberId = 1L,
            paymentPendingExpiresAt = now.minusMinutes(1),
        )

        assertThatThrownBy {
            service.expireAll(courseIds = listOf(1L), now = now)
        }
            .isInstanceOf(CourseInvalidStateException::class.java)
            .hasMessage("좌석을 반환할 수 없습니다.")

        assertThat(enrollmentRepository.findById(1L)?.status).isEqualTo(EnrollmentStatus.EXPIRED)
        assertThat(courseRepository.findById(1L)?.seatLeftCount).isEqualTo(2)
    }

    private fun givenCourse(
        courseId: Long,
        seatLeftCount: Int,
        capacity: Int,
        status: CourseStatus = CourseStatus.OPEN,
    ) {
        courseRepository.save(
            course(
                courseId = courseId,
                seatLeftCount = seatLeftCount,
                capacity = capacity,
                status = status,
            ),
        )
    }

    private fun course(
        courseId: Long,
        seatLeftCount: Int,
        capacity: Int,
        status: CourseStatus = CourseStatus.OPEN,
    ) = CourseModelData(
        courseId = courseId,
        creatorId = 1L,
        title = "Kotlin Basic $courseId",
        description = "Kotlin course $courseId",
        price = Money(100_000L),
        capacity = capacity,
        seatLeftCount = seatLeftCount,
        periodStart = LocalDateTime.of(2026, 6, 1, 0, 0),
        periodEnd = LocalDateTime.of(2026, 6, 30, 0, 0),
        status = status,
    )

    private fun givenPendingEnrollment(
        enrollmentId: Long,
        courseId: Long,
        memberId: Long,
        paymentPendingExpiresAt: LocalDateTime,
    ) {
        givenEnrollment(
            enrollmentId = enrollmentId,
            courseId = courseId,
            memberId = memberId,
            status = EnrollmentStatus.PENDING,
            paymentPendingExpiresAt = paymentPendingExpiresAt,
        )
    }

    private fun givenEnrollment(
        enrollmentId: Long,
        courseId: Long,
        memberId: Long,
        status: EnrollmentStatus,
        paymentPendingExpiresAt: LocalDateTime,
    ) {
        enrollmentRepository.save(
            EnrollmentModelData(
                enrollmentId = enrollmentId,
                courseId = courseId,
                memberId = memberId,
                status = status,
                paymentPendingStartedAt = paymentPendingExpiresAt.minusMinutes(10),
                paymentPendingExpiresAt = paymentPendingExpiresAt,
            ),
        )
    }
}
