package org.yechan.enrollment

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.yechan.FakeCourseRepository
import org.yechan.FakeEnrollmentRepository
import org.yechan.FakeEnrollmentWaitlistRepository
import org.yechan.course.CourseModelData
import org.yechan.course.CourseService
import org.yechan.course.CourseStatus
import org.yechan.course.CourseStatusCommand
import org.yechan.course.CreateCourseCommand
import org.yechan.course.Money
import org.yechan.member.MemberModel
import org.yechan.member.MemberModelData
import org.yechan.member.MemberRepository
import org.yechan.member.MemberRole
import java.time.Duration
import java.time.Instant
import java.time.LocalDateTime

class EnrollmentWaitlistSchedulerTest {
    private val memberRepository = FakeMemberRepository()
    private val courseRepository = FakeCourseRepository()
    private val enrollmentRepository = FakeEnrollmentRepository()
    private val courseBulkWriter = courseRepository
    private val waitlistRepository = FakeEnrollmentWaitlistRepository()
    private val courseService = CourseService(memberRepository, courseRepository)
    private val enrollmentTransactionService =
        EnrollmentTransactionService(courseRepository, enrollmentRepository)
    private val enrollmentService =
        EnrollmentService(enrollmentTransactionService, waitlistRepository)
    private val enrollmentWaitlistProcessor =
        EnrollmentWaitlistPromotionService(
            courseBulkWriter,
            enrollmentRepository,
            enrollmentRepository,
        )
    private val scheduler = EnrollmentWaitlistScheduler(
        waitlistRepository,
        courseRepository,
        enrollmentWaitlistProcessor,
    )

    @Test
    fun `스케줄러는 좌석이 생기면 대기열의 가장 오래된 회원부터 예약한다`() {
        memberRepository.save(member(id = 1L, role = MemberRole.CREATOR))
        memberRepository.save(member(id = 2L, role = MemberRole.CLASSMATE))
        memberRepository.save(member(id = 3L, role = MemberRole.CLASSMATE))
        val course = courseService.createCourse(createCourseCommand(capacity = 1), 1L)
        courseService.openCourse(CourseStatusCommand(memberId = 1L, courseId = course.courseId))

        waitlistRepository.enqueue(course.courseId, 2L, Instant.parse("2026-01-01T00:00:00Z"))
        waitlistRepository.enqueue(course.courseId, 3L, Instant.parse("2026-01-01T00:00:01Z"))

        scheduler.processWaitlists()

        val changedCourse = courseService.getCourse(course.courseId)
        val enrollments =
            enrollmentRepository.enrollments.values.filter { it.memberId == 2L }.toList()

        assertThat(changedCourse.seatLeftCount).isEqualTo(0)
        assertThat(enrollments).hasSize(1)
        assertThat(waitlistRepository.findCourseIds()).containsExactly(course.courseId)
        assertThat(waitlistRepository.pop(course.courseId)?.memberId).isEqualTo(3L)
    }

    @Test
    fun `좌석이 없는 강의는 대기열을 처리하지 않는다`() {
        memberRepository.save(member(id = 1L, role = MemberRole.CREATOR))
        memberRepository.save(member(id = 2L, role = MemberRole.CLASSMATE))

        val course = courseService.createCourse(createCourseCommand(capacity = 1), 1L)
        courseService.openCourse(CourseStatusCommand(memberId = 1L, courseId = course.courseId))

        courseRepository.reserveSeatIfAvailable(course.courseId)

        waitlistRepository.enqueue(
            courseId = course.courseId,
            memberId = 2L,
            requestedAt = Instant.parse("2026-01-01T00:00:00Z"),
        )

        scheduler.processWaitlists()

        val changedCourse = courseService.getCourse(course.courseId)
        val enrollments = enrollmentRepository.enrollments.values
            .filter { it.memberId == 2L }

        assertThat(changedCourse.seatLeftCount).isEqualTo(0)
        assertThat(enrollments).isEmpty()
        assertThat(waitlistRepository.findCourseIds()).containsExactly(course.courseId)
        assertThat(waitlistRepository.pop(course.courseId)?.memberId).isEqualTo(2L)
    }

    @Test
    fun `수강 취소로 좌석이 생기면 대기열의 가장 오래된 회원부터 승격된다`() {
        memberRepository.save(member(id = 1L, role = MemberRole.CREATOR))
        memberRepository.save(member(id = 2L, role = MemberRole.CLASSMATE))
        memberRepository.save(member(id = 3L, role = MemberRole.CLASSMATE))
        memberRepository.save(member(id = 4L, role = MemberRole.CLASSMATE))
        val course = courseService.createCourse(createCourseCommand(capacity = 1), 1L)
        courseService.openCourse(CourseStatusCommand(memberId = 1L, courseId = course.courseId))

        val firstEnrollment =
            enrollmentService.enroll(
                EnrollCourseCommand(
                    memberId = 2L,
                    courseId = course.courseId,
                ),
            ).enrollment
        val firstWaitlisted =
            enrollmentService.enroll(EnrollCourseCommand(memberId = 3L, courseId = course.courseId))
        val secondWaitlisted =
            enrollmentService.enroll(EnrollCourseCommand(memberId = 4L, courseId = course.courseId))

        enrollmentService.cancelEnrollment(
            EnrollmentStatusCommand(
                memberId = 2L,
                enrollmentId = firstEnrollment.enrollmentId,
            ),
        )

        scheduler.processWaitlists()

        val changedCourse = courseService.getCourse(course.courseId)
        val promotedEnrollments =
            enrollmentRepository.enrollments.values.filter { it.memberId == 3L }

        assertThat(firstWaitlisted).isEqualTo(
            EnrollResult.Waitlisted(
                courseId = course.courseId,
                memberId = 3L,
            ),
        )
        assertThat(secondWaitlisted).isEqualTo(
            EnrollResult.Waitlisted(
                courseId = course.courseId,
                memberId = 4L,
            ),
        )
        assertThat(changedCourse.seatLeftCount).isEqualTo(0)
        assertThat(promotedEnrollments).hasSize(1)
        assertThat(promotedEnrollments.single().status).isEqualTo(EnrollmentStatus.PENDING)
        assertThat(waitlistRepository.findByMemberId(4L)).hasSize(1)
    }

    @Test
    fun `승격 중 좌석 예약 실패 시 pop된 대기자를 복구한다`() {
        memberRepository.save(member(id = 1L, role = MemberRole.CREATOR))
        memberRepository.save(member(id = 2L, role = MemberRole.CLASSMATE))
        memberRepository.save(member(id = 3L, role = MemberRole.CLASSMATE))
        val course = courseService.createCourse(createCourseCommand(capacity = 1), 1L)
        courseService.openCourse(CourseStatusCommand(memberId = 1L, courseId = course.courseId))

        waitlistRepository.enqueue(course.courseId, 2L, Instant.parse("2026-01-01T00:00:00Z"))
        waitlistRepository.enqueue(course.courseId, 3L, Instant.parse("2026-01-01T00:00:01Z"))

        val failingProcessor = EnrollmentWaitlistPromotionService(
            object : CourseBulkWriter {
                override fun reserveSeatsBulk(courseIds: Map<Long, Int>): Unit = throw IllegalStateException("좌석 예약 실패")

                override fun releaseSeatsBulk(courseIds: Map<Long, Int>) = Unit
            },
            enrollmentRepository,
            enrollmentRepository,
        )
        val failingScheduler = EnrollmentWaitlistScheduler(
            waitlistRepository,
            courseRepository,
            failingProcessor,
        )

        assertThatThrownBy { failingScheduler.processWaitlists() }
            .isInstanceOf(IllegalStateException::class.java)

        assertThat(waitlistRepository.findByMemberId(2L)).hasSize(1)
        assertThat(waitlistRepository.findByMemberId(3L)).hasSize(1)
        assertThat(waitlistRepository.isSoldOut(course.courseId)).isTrue()
        assertThat(enrollmentRepository.findByMemberId(2L)).isEmpty()
    }

    @Test
    fun `승격된 신청의 결제 대기 만료 시간은 설정값을 따른다`() {
        memberRepository.save(member(id = 1L, role = MemberRole.CREATOR))
        memberRepository.save(member(id = 2L, role = MemberRole.CLASSMATE))
        memberRepository.save(member(id = 3L, role = MemberRole.CLASSMATE))
        val course = courseService.createCourse(createCourseCommand(capacity = 1), 1L)
        courseService.openCourse(CourseStatusCommand(memberId = 1L, courseId = course.courseId))

        val enrollment = enrollmentService.enroll(
            EnrollCourseCommand(
                memberId = 2L,
                courseId = course.courseId,
            ),
        ).enrollment
        waitlistRepository.enqueue(course.courseId, 3L, Instant.parse("2026-01-01T00:00:00Z"))

        enrollmentService.cancelEnrollment(
            EnrollmentStatusCommand(
                memberId = 2L,
                enrollmentId = enrollment.enrollmentId,
            ),
        )

        val customProcessor = EnrollmentWaitlistPromotionService(
            courseBulkWriter,
            enrollmentRepository,
            enrollmentRepository,
            Duration.ofMinutes(3),
        )
        val customScheduler = EnrollmentWaitlistScheduler(
            waitlistRepository,
            courseRepository,
            customProcessor,
        )

        customScheduler.processWaitlists()

        val promotedEnrollment = enrollmentRepository.enrollments.values.first { it.memberId == 3L }

        assertThat(
            Duration.between(
                promotedEnrollment.paymentPendingStartedAt,
                promotedEnrollment.paymentPendingExpiresAt,
            ),
        )
            .isEqualTo(Duration.ofMinutes(3))
    }

    @Test
    fun `만료된 신청이 남아 있어도 스케줄러는 같은 row를 재사용한다`() {
        memberRepository.save(member(id = 1L, role = MemberRole.CREATOR))
        memberRepository.save(member(id = 2L, role = MemberRole.CLASSMATE))
        val course = courseService.createCourse(createCourseCommand(capacity = 1), 1L)
        courseService.openCourse(CourseStatusCommand(memberId = 1L, courseId = course.courseId))

        val expiredEnrollment = enrollmentRepository.save(
            EnrollmentModelData(
                courseId = course.courseId,
                memberId = 2L,
                status = EnrollmentStatus.EXPIRED,
            ),
        )
        waitlistRepository.enqueue(course.courseId, 2L, Instant.parse("2026-01-01T00:00:00Z"))

        scheduler.processWaitlists()

        val reactivatedEnrollment =
            enrollmentRepository.findById(requireNotNull(expiredEnrollment.enrollmentId))

        assertThat(reactivatedEnrollment?.status).isEqualTo(EnrollmentStatus.PENDING)
        assertThat(reactivatedEnrollment?.enrollmentId).isEqualTo(expiredEnrollment.enrollmentId)
        assertThat(courseService.getCourse(course.courseId).seatLeftCount).isEqualTo(0)
    }

    @Test
    fun `대기열이 비어 있으면 아무 작업도 하지 않는다`() {
        val course = courseRepository.save(
            CourseModelData(
                courseId = 1L,
                capacity = 10,
                creatorId = 1L,
                seatLeftCount = 10,
                status = CourseStatus.OPEN,
                title = "Kotlin Basic",
                description = "Kotlin course",
                price = Money(100_000L),
                periodStart = LocalDateTime.of(2026, 6, 1, 0, 0),
                periodEnd = LocalDateTime.of(2026, 6, 30, 0, 0),
            ),
        )

        scheduler.processWaitlists()

        val changedCourse = courseRepository.findById(course.courseId!!)

        assertThat(changedCourse?.seatLeftCount).isEqualTo(10)
        assertThat(enrollmentRepository.findByMemberId(1L)).isEmpty()
    }

    private fun createCourseCommand(
        capacity: Int = 2,
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
    ) = MemberModelData(
        memberId = id,
        email = "user$id@example.com",
        passwordHash = "hash",
        name = "user$id",
        role = role,
    )

    private class FakeMemberRepository : MemberRepository {
        private val members = linkedMapOf<Long, MemberModel>()

        override fun save(member: MemberModel): MemberModel {
            members[requireNotNull(member.memberId)] = member
            return member
        }

        override fun existsByEmail(email: String): Boolean = members.values.any { it.email == email }

        override fun findByEmail(email: String): MemberModel? = members.values.firstOrNull { it.email == email }

        override fun findById(id: Long): MemberModel? = members[id]
    }
}
