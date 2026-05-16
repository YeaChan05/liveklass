package org.yechan.enrollment

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.yechan.FakeCourseRepository
import org.yechan.FakeEnrollmentRepository
import org.yechan.FakeEnrollmentWaitlistRepository
import org.yechan.FakeMemberRepository
import org.yechan.course.CourseService
import org.yechan.course.CourseStatusCommand
import org.yechan.course.CreateCourseCommand
import org.yechan.course.Money
import org.yechan.member.MemberModelData
import org.yechan.member.MemberRole
import org.yechan.member.MemberStatus
import java.time.Clock
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneOffset

class EnrollmentPaymentExpirationSchedulerTest {
    private val now = LocalDateTime.of(2026, 1, 1, 12, 0)
    private val clock = Clock.fixed(Instant.parse("2026-01-01T12:00:00Z"), ZoneOffset.UTC)
    private val memberRepository = FakeMemberRepository()
    private val courseRepository = FakeCourseRepository()
    private val enrollmentRepository = FakeEnrollmentRepository()
    private val waitlistRepository = FakeEnrollmentWaitlistRepository()
    private val courseService = CourseService(memberRepository, courseRepository)
    private val enrollmentTransactionService =
        EnrollmentTransactionService(courseRepository, enrollmentRepository)
    private val enrollmentService =
        EnrollmentService(enrollmentTransactionService, waitlistRepository)

    @Test
    fun `만료 스케줄러는 실제 만료 처리를 통해 좌석을 반환하고 sold out 상태를 해제한다`() {
        memberRepository.save(member(id = 1L, role = MemberRole.CREATOR))
        memberRepository.save(member(id = 2L, role = MemberRole.CLASSMATE))
        val course = courseService.createCourse(createCourseCommand(), 1L)
        courseService.openCourse(CourseStatusCommand(memberId = 1L, courseId = course.courseId))

        val enrolled = enrollmentService.enroll(
            EnrollCourseCommand(
                memberId = 2L,
                courseId = course.courseId,
            ),
        ).enrollment
        enrollmentRepository.enrollments[enrolled.enrollmentId] = EnrollmentModelData(
            enrollmentId = enrolled.enrollmentId,
            courseId = enrolled.courseId,
            memberId = enrolled.memberId,
            status = EnrollmentStatus.PENDING,
            paymentPendingStartedAt = now.minusMinutes(10),
            paymentPendingExpiresAt = now.minusMinutes(1),
        )

        waitlistRepository.markSoldOut(course.courseId)

        val scheduler = EnrollmentPaymentExpirationScheduler(
            enrollmentRepository = enrollmentRepository,
            enrollmentExpirationProcessor = EnrollmentExpirationService(
                enrollmentBulkWriter = enrollmentRepository,
                courseBulkWriter = courseRepository,
            ),
            waitlistRepository = waitlistRepository,
            clock = clock,
        )

        scheduler.expirePaymentPendingEnrollments()

        val changedCourse = courseRepository.findById(course.courseId)
        val changedEnrollment = enrollmentRepository.findById(enrolled.enrollmentId)

        assertThat(changedEnrollment?.status).isEqualTo(EnrollmentStatus.EXPIRED)
        assertThat(changedCourse?.seatLeftCount).isEqualTo(1)
        assertThat(waitlistRepository.isSoldOut(course.courseId)).isFalse()
    }

    @Test
    fun `만료 대상이 없으면 processor를 호출하지 않는다`() {
        val scheduler = EnrollmentPaymentExpirationScheduler(
            enrollmentRepository = enrollmentRepository,
            enrollmentExpirationProcessor = EnrollmentExpirationService(
                enrollmentBulkWriter = enrollmentRepository,
                courseBulkWriter = courseRepository,
            ),
            waitlistRepository = waitlistRepository,
            clock = clock,
        )

        scheduler.expirePaymentPendingEnrollments()

        assertThat(waitlistRepository.findCourseIds()).isEmpty()
    }

    private fun createCourseCommand(
        capacity: Int = 1,
    ) = CreateCourseCommand(
        title = "Kotlin Basic",
        description = "Kotlin course",
        price = Money(100_000L),
        capacity = capacity,
        periodStart = LocalDateTime.of(2026, 6, 1, 0, 0),
        periodEnd = LocalDateTime.of(2026, 6, 30, 0, 0),
    )

    private fun member(
        id: Long,
        role: MemberRole,
        status: MemberStatus = MemberStatus.ACTIVE,
    ) = MemberModelData(
        memberId = id,
        email = "user$id@example.com",
        passwordHash = "hash",
        name = "user$id",
        role = role,
        status = status,
    )
}
