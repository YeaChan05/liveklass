package org.yechan.enrollment

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.yechan.FakeCourseRepository
import org.yechan.FakeEnrollmentRepository
import org.yechan.FakeEnrollmentWaitlistRepository
import org.yechan.course.CourseInvalidStateException
import org.yechan.course.CourseModelData
import org.yechan.course.CourseStatus
import org.yechan.course.Money
import java.time.LocalDateTime

class EnrollmentExpirationServiceTest {
    private lateinit var enrollmentRepository: FakeEnrollmentRepository
    private lateinit var courseRepository: FakeCourseRepository
    private lateinit var waitlistRepository: FakeEnrollmentWaitlistRepository
    private lateinit var service: EnrollmentExpirationService

    @BeforeEach
    fun setUp() {
        enrollmentRepository = FakeEnrollmentRepository()
        courseRepository = FakeCourseRepository()
        waitlistRepository = FakeEnrollmentWaitlistRepository()

        service = EnrollmentExpirationService(
            enrollmentRepository = enrollmentRepository,
            courseRepository = courseRepository,
            waitlistRepository = waitlistRepository,
        )
    }

    @Test
    fun `만료 대상이 없으면 아무것도 처리하지 않는다`() {
        val now = LocalDateTime.of(2026, 5, 11, 12, 0)

        val result = service.expirePaymentPendingEnrollments(now)

        assertThat(result).isZero()
    }

    @Test
    fun `만료된 결제 대기 신청을 만료 처리하고 좌석을 반환한다`() {
        val now = LocalDateTime.of(2026, 5, 11, 12, 0)

        val course = courseRepository.save(
            course(
                capacity = 1,
                seatLeftCount = 0,
                status = CourseStatus.OPEN,
            ),
        )

        val courseId = requireNotNull(course.courseId)

        waitlistRepository.markSoldOut(courseId)

        val enrollment = enrollmentRepository.save(
            enrollment(
                courseId = courseId,
                memberId = 2L,
                paymentPendingStartedAt = now.minusMinutes(20),
                paymentPendingExpiresAt = now.minusMinutes(10),
            ),
            courseId,
        )

        val result = service.expirePaymentPendingEnrollments(now)

        assertThat(result).isEqualTo(1)

        assertThat(enrollmentRepository.findById(requireNotNull(enrollment.enrollmentId)))
            .extracting { it?.status }
            .isEqualTo(EnrollmentStatus.EXPIRED)

        assertThat(courseRepository.findById(courseId))
            .extracting { it?.seatLeftCount }
            .isEqualTo(1)

        assertThat(waitlistRepository.isSoldOut(courseId)).isFalse()
    }

    @Test
    fun `아직 만료되지 않은 결제 대기 신청은 처리하지 않는다`() {
        val now = LocalDateTime.of(2026, 5, 11, 12, 0)

        val course = courseRepository.save(
            course(
                capacity = 1,
                seatLeftCount = 0,
                status = CourseStatus.OPEN,
            ),
        )

        val courseId = requireNotNull(course.courseId)

        waitlistRepository.markSoldOut(courseId)

        val enrollment = enrollmentRepository.save(
            enrollment(
                courseId = courseId,
                memberId = 2L,
                paymentPendingStartedAt = now.minusMinutes(5),
                paymentPendingExpiresAt = now.plusMinutes(5),
            ),
            courseId,
        )

        val result = service.expirePaymentPendingEnrollments(now)

        assertThat(result).isZero()

        assertThat(enrollmentRepository.findById(requireNotNull(enrollment.enrollmentId)))
            .extracting { it?.status }
            .isEqualTo(EnrollmentStatus.PENDING)

        assertThat(courseRepository.findById(courseId))
            .extracting { it?.seatLeftCount }
            .isEqualTo(0)

        assertThat(waitlistRepository.isSoldOut(courseId)).isTrue()
    }

    @Test
    fun `좌석 반환에 실패하면 예외가 발생한다`() {
        val now = LocalDateTime.of(2026, 5, 11, 12, 0)

        val course = courseRepository.save(
            course(
                capacity = 1,
                seatLeftCount = 1,
                status = CourseStatus.OPEN,
            ),
        )

        val courseId = requireNotNull(course.courseId)

        val enrollment = enrollmentRepository.save(
            enrollment(
                courseId = courseId,
                memberId = 2L,
                paymentPendingStartedAt = now.minusMinutes(20),
                paymentPendingExpiresAt = now.minusMinutes(10),
            ),
            courseId,
        )

        assertThatThrownBy {
            service.expirePaymentPendingEnrollments(now)
        }.isInstanceOf(CourseInvalidStateException::class.java)

        assertThat(enrollmentRepository.findById(requireNotNull(enrollment.enrollmentId)))
            .extracting { it?.status }
            .isEqualTo(EnrollmentStatus.EXPIRED)

        assertThat(courseRepository.findById(courseId))
            .extracting { it?.seatLeftCount }
            .isEqualTo(1)
    }

    @Test
    fun `여러 만료 대상이 있으면 모두 처리한다`() {
        val now = LocalDateTime.of(2026, 5, 11, 12, 0)

        val firstCourse = courseRepository.save(
            course(
                capacity = 1,
                seatLeftCount = 0,
                status = CourseStatus.OPEN,
            ),
        )

        val secondCourse = courseRepository.save(
            course(
                capacity = 1,
                seatLeftCount = 0,
                status = CourseStatus.OPEN,
            ),
        )

        val firstCourseId = requireNotNull(firstCourse.courseId)
        val secondCourseId = requireNotNull(secondCourse.courseId)

        waitlistRepository.markSoldOut(firstCourseId)
        waitlistRepository.markSoldOut(secondCourseId)

        enrollmentRepository.save(
            enrollment(
                courseId = firstCourseId,
                memberId = 2L,
                paymentPendingStartedAt = now.minusMinutes(30),
                paymentPendingExpiresAt = now.minusMinutes(20),
            ),
            firstCourseId,
        )

        enrollmentRepository.save(
            enrollment(
                courseId = secondCourseId,
                memberId = 3L,
                paymentPendingStartedAt = now.minusMinutes(20),
                paymentPendingExpiresAt = now.minusMinutes(10),
            ),
            secondCourseId,
        )

        val result = service.expirePaymentPendingEnrollments(now)

        assertThat(result).isEqualTo(2)

        assertThat(courseRepository.findById(firstCourseId))
            .extracting { it?.seatLeftCount }
            .isEqualTo(1)

        assertThat(courseRepository.findById(secondCourseId))
            .extracting { it?.seatLeftCount }
            .isEqualTo(1)

        assertThat(waitlistRepository.isSoldOut(firstCourseId)).isFalse()
        assertThat(waitlistRepository.isSoldOut(secondCourseId)).isFalse()
    }

    private fun course(
        capacity: Int = 10,
        seatLeftCount: Int = capacity,
        status: CourseStatus = CourseStatus.OPEN,
    ): CourseModelData = CourseModelData(
        creatorId = 1L,
        title = "Kotlin Basic",
        description = "Kotlin course",
        price = Money(100000),
        capacity = capacity,
        seatLeftCount = seatLeftCount,
        periodStart = LocalDateTime.of(2099, 6, 1, 0, 0),
        periodEnd = LocalDateTime.of(2099, 6, 30, 0, 0),
        status = status,
    )

    private fun enrollment(
        courseId: Long,
        memberId: Long,
        paymentPendingStartedAt: LocalDateTime,
        paymentPendingExpiresAt: LocalDateTime,
    ): EnrollmentModelData = EnrollmentModelData(
        courseId = courseId,
        memberId = memberId,
        status = EnrollmentStatus.PENDING,
        paymentPendingStartedAt = paymentPendingStartedAt,
        paymentPendingExpiresAt = paymentPendingExpiresAt,
    )
}
