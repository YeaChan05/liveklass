package org.yechan.enrollment

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.yechan.FakeCourseRepository
import org.yechan.FakeEnrollmentRepository
import org.yechan.FakeEnrollmentWaitlistRepository
import org.yechan.FakeMemberRepository
import org.yechan.course.CourseRepositoryReader
import org.yechan.course.CourseRepositoryWriter
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
    private val courseService = CourseService(
        CourseRepositoryReader(courseRepository),
        CourseRepositoryWriter(memberRepository, courseRepository),
    )
    private val enrollmentReader =
        EnrollmentRepositoryReader(courseRepository, enrollmentRepository)
    private val enrollmentWriter =
        EnrollmentRepositoryWriter(courseRepository, enrollmentRepository)
    private val waitlistReader =
        EnrollmentWaitlistRepositoryReader(waitlistRepository)
    private val waitlistWriter =
        EnrollmentWaitlistRepositoryWriter(
            waitlistRepository,
            EnrollmentWaitlistPromotionService(
                courseRepository,
                enrollmentRepository,
                enrollmentRepository,
            ),
        )
    private val enrollmentService =
        EnrollmentService(enrollmentReader, enrollmentWriter, waitlistReader, waitlistWriter)

    @Test
    fun `결제 만료 스케줄러는 전용 서비스만 호출한다`() {
        val expirationService = RecordingPaymentExpirationUseCase()
        val scheduler = EnrollmentPaymentExpirationScheduler(expirationService)

        scheduler.expirePaymentPendingEnrollments()

        assertThat(expirationService.callCount).isEqualTo(1)
    }

    @Test
    fun `결제 만료 서비스는 실제 만료 처리를 통해 좌석을 반환하고 waitlist mode를 해제한다`() {
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

        val expirationService = EnrollmentPaymentExpirationService(
            enrollmentReader = enrollmentReader,
            enrollmentExpirationProcessor = EnrollmentExpirationService(
                enrollmentBulkWriter = enrollmentRepository,
                courseBulkWriter = courseRepository,
            ),
            waitlistWriter = waitlistWriter,
            clock = clock,
        )

        expirationService.expirePaymentPendingEnrollments()

        val changedCourse = courseRepository.findById(course.courseId)
        val changedEnrollment = enrollmentRepository.findById(enrolled.enrollmentId)

        assertThat(changedEnrollment?.status).isEqualTo(EnrollmentStatus.EXPIRED)
        assertThat(changedCourse?.seatLeftCount).isEqualTo(1)
        assertThat(waitlistRepository.isSoldOut(course.courseId)).isFalse()
    }

    @Test
    fun `결제 만료로 반환된 좌석은 신규 신청자가 아니라 대기열 선두 회원에게 먼저 배정된다`() {
        memberRepository.save(member(id = 1L, role = MemberRole.CREATOR))
        memberRepository.save(member(id = 2L, role = MemberRole.CLASSMATE))
        memberRepository.save(member(id = 3L, role = MemberRole.CLASSMATE))
        memberRepository.save(member(id = 4L, role = MemberRole.CLASSMATE))
        val course = courseService.createCourse(createCourseCommand(capacity = 1), 1L)
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
        enrollmentService.enroll(EnrollCourseCommand(memberId = 3L, courseId = course.courseId))
        enrollmentService.enroll(EnrollCourseCommand(memberId = 4L, courseId = course.courseId))

        val expirationService = EnrollmentPaymentExpirationService(
            enrollmentReader = enrollmentReader,
            enrollmentExpirationProcessor = EnrollmentExpirationService(
                enrollmentBulkWriter = enrollmentRepository,
                courseBulkWriter = courseRepository,
            ),
            waitlistWriter = waitlistWriter,
            clock = clock,
        )

        expirationService.expirePaymentPendingEnrollments()

        val assigned = enrollmentRepository.findByMemberIdAndCourseId(
            memberId = 3L,
            courseId = course.courseId,
        )

        assertThat(enrollmentRepository.findById(enrolled.enrollmentId)?.status).isEqualTo(EnrollmentStatus.EXPIRED)
        assertThat(assigned?.status).isEqualTo(EnrollmentStatus.PENDING)
        assertThat(courseRepository.findById(course.courseId)?.seatLeftCount).isEqualTo(0)
        assertThat(waitlistRepository.findByMemberId(3L)).isEmpty()
        assertThat(waitlistRepository.findByMemberId(4L)).hasSize(1)
        assertThat(waitlistRepository.isSoldOut(course.courseId)).isTrue()
    }

    @Test
    fun `만료 대상이 없으면 processor를 호출하지 않는다`() {
        val expirationService = EnrollmentPaymentExpirationService(
            enrollmentReader = enrollmentReader,
            enrollmentExpirationProcessor = EnrollmentExpirationService(
                enrollmentBulkWriter = enrollmentRepository,
                courseBulkWriter = courseRepository,
            ),
            waitlistWriter = waitlistWriter,
            clock = clock,
        )

        expirationService.expirePaymentPendingEnrollments()

        assertThat(waitlistRepository.findCourseIds()).isEmpty()
    }

    private class RecordingPaymentExpirationUseCase : EnrollmentPaymentExpirationUseCase {
        var callCount = 0
            private set

        override fun expirePaymentPendingEnrollments() {
            callCount++
        }
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
