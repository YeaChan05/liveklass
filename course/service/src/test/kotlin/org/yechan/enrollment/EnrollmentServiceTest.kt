package org.yechan.enrollment

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import org.yechan.FakeCourseRepository
import org.yechan.FakeEnrollmentRepository
import org.yechan.FakeEnrollmentWaitlistRepository
import org.yechan.FakeMemberRepository
import org.yechan.course.CourseInvalidStateException
import org.yechan.course.CourseNotFoundException
import org.yechan.course.CourseService
import org.yechan.course.CourseStatusCommand
import org.yechan.course.CreateCourseCommand
import org.yechan.course.EnrollmentNotFoundException
import org.yechan.course.Money
import org.yechan.member.MemberModelData
import org.yechan.member.MemberRole
import org.yechan.member.MemberStatus
import java.time.LocalDateTime

class EnrollmentServiceTest {
    private val memberRepository = FakeMemberRepository()
    private val courseRepository = FakeCourseRepository()
    private val enrollmentRepository = FakeEnrollmentRepository()
    private val waitlistRepository = FakeEnrollmentWaitlistRepository()
    private val courseService = CourseService(memberRepository, courseRepository)
    private val service =
        EnrollmentService(
            courseRepository,
            enrollmentRepository,
            waitlistRepository,
        )

    @Test
    fun `클래스메이트는 모집 중인 강의를 신청하면 결제 대기 신청이 생성되고 좌석이 감소한다`() {
        memberRepository.save(member(id = 1L, role = MemberRole.CREATOR))
        memberRepository.save(member(id = 2L, role = MemberRole.CLASSMATE))
        val course = courseService.createCourse(createCourseCommand(), 1L)
        courseService.openCourse(CourseStatusCommand(memberId = 1L, courseId = course.courseId))

        val enrollment =
            service.enroll(EnrollCourseCommand(memberId = 2L, courseId = course.courseId))
        val changedCourse = courseService.getCourse(course.courseId)

        assertEquals(EnrollmentStatus.PENDING, enrollment.status)
        assertEquals(1, changedCourse.seatLeftCount)
    }

    @Test
    fun `남은 좌석이 없으면 수강 신청은 실패한다`() {
        memberRepository.save(member(id = 1L, role = MemberRole.CREATOR))
        memberRepository.save(member(id = 2L, role = MemberRole.CLASSMATE))
        memberRepository.save(member(id = 3L, role = MemberRole.CLASSMATE))
        val course = courseService.createCourse(createCourseCommand(capacity = 1), 1L)
        courseService.openCourse(CourseStatusCommand(memberId = 1L, courseId = course.courseId))
        service.enroll(EnrollCourseCommand(memberId = 2L, courseId = course.courseId))

        val exception = assertThrows(CourseInvalidStateException::class.java) {
            service.enroll(EnrollCourseCommand(memberId = 3L, courseId = course.courseId))
        }

        assertEquals("강의 정원을 초과할 수 없습니다.", exception.message)
        assertEquals(3L, waitlistRepository.pop(course.courseId)?.memberId)
    }

    @Test
    fun `결제 확정과 수강 취소는 신청 상태를 바꾸고 취소 시 좌석을 되돌린다`() {
        memberRepository.save(member(id = 1L, role = MemberRole.CREATOR))
        memberRepository.save(member(id = 2L, role = MemberRole.CLASSMATE))
        val course = courseService.createCourse(createCourseCommand(), 1L)
        courseService.openCourse(CourseStatusCommand(memberId = 1L, courseId = course.courseId))
        val enrollment =
            service.enroll(EnrollCourseCommand(memberId = 2L, courseId = course.courseId))

        val confirmed =
            service.confirmEnrollment(EnrollmentStatusCommand(2L, enrollment.enrollmentId))
        val cancelled =
            service.cancelEnrollment(EnrollmentStatusCommand(2L, enrollment.enrollmentId))
        val changedCourse = courseService.getCourse(course.courseId)

        assertEquals(EnrollmentStatus.CONFIRMED, confirmed.status)
        assertEquals(EnrollmentStatus.CANCELLED, cancelled.status)
        assertEquals(2, changedCourse.seatLeftCount)
    }

    @Test
    fun `내 수강 신청 목록을 조회한다`() {
        memberRepository.save(member(id = 1L, role = MemberRole.CREATOR))
        memberRepository.save(member(id = 2L, role = MemberRole.CLASSMATE))
        val course = courseService.createCourse(createCourseCommand(), 1L)
        courseService.openCourse(CourseStatusCommand(memberId = 1L, courseId = course.courseId))
        service.enroll(EnrollCourseCommand(memberId = 2L, courseId = course.courseId))

        val enrollments = service.getMyEnrollments(2L)

        assertEquals(1, enrollments.size)
        assertEquals(2L, enrollments.single().memberId)
    }

    @Test
    fun `크리에이터는 수강생이 가능한 수강 신청 상태 변경 목록 조회를 할 수 있다`() {
        memberRepository.save(member(id = 1L, role = MemberRole.CREATOR))
        val course = courseService.createCourse(createCourseCommand(), 1L)
        courseService.openCourse(CourseStatusCommand(memberId = 1L, courseId = course.courseId))

        val enrollment =
            service.enroll(EnrollCourseCommand(memberId = 1L, courseId = course.courseId))
        val confirmed =
            service.confirmEnrollment(EnrollmentStatusCommand(1L, enrollment.enrollmentId))
        val cancelled =
            service.cancelEnrollment(EnrollmentStatusCommand(1L, enrollment.enrollmentId))
        val enrollments = service.getMyEnrollments(1L)

        assertEquals(EnrollmentStatus.PENDING, enrollment.status)
        assertEquals(EnrollmentStatus.CONFIRMED, confirmed.status)
        assertEquals(EnrollmentStatus.CANCELLED, cancelled.status)
        assertEquals(1, enrollments.size)
    }

    @Test
    fun `존재하지 않는 강의는 수강 신청할 수 없다`() {
        memberRepository.save(member(id = 2L, role = MemberRole.CLASSMATE))

        assertThrows(CourseNotFoundException::class.java) {
            service.enroll(EnrollCourseCommand(memberId = 2L, courseId = 999L))
        }
    }

    @Test
    fun `다른 회원의 수강 신청은 확정할 수 없다`() {
        memberRepository.save(member(id = 1L, role = MemberRole.CREATOR))
        memberRepository.save(member(id = 2L, role = MemberRole.CLASSMATE))
        memberRepository.save(member(id = 3L, role = MemberRole.CLASSMATE))
        val course = courseService.createCourse(createCourseCommand(), 1L)
        courseService.openCourse(CourseStatusCommand(memberId = 1L, courseId = course.courseId))
        val enrollment =
            service.enroll(EnrollCourseCommand(memberId = 2L, courseId = course.courseId))

        assertThrows(EnrollmentNotFoundException::class.java) {
            service.confirmEnrollment(
                EnrollmentStatusCommand(
                    memberId = 3L,
                    enrollmentId = enrollment.enrollmentId,
                ),
            )
        }
    }

    @Test
    fun `확정된 신청은 다시 확정할 수 없다`() {
        memberRepository.save(member(id = 1L, role = MemberRole.CREATOR))
        memberRepository.save(member(id = 2L, role = MemberRole.CLASSMATE))
        val course = courseService.createCourse(createCourseCommand(), 1L)
        courseService.openCourse(CourseStatusCommand(memberId = 1L, courseId = course.courseId))
        val enrollment =
            service.enroll(EnrollCourseCommand(memberId = 2L, courseId = course.courseId))
        service.confirmEnrollment(
            EnrollmentStatusCommand(
                memberId = 2L,
                enrollmentId = enrollment.enrollmentId,
            ),
        )

        val exception = assertThrows(CourseInvalidStateException::class.java) {
            service.confirmEnrollment(
                EnrollmentStatusCommand(
                    memberId = 2L,
                    enrollmentId = enrollment.enrollmentId,
                ),
            )
        }

        assertEquals("결제 대기 상태의 신청만 확정할 수 있습니다.", exception.message)
    }

    @Test
    fun `취소된 신청은 다시 취소할 수 없다`() {
        memberRepository.save(member(id = 1L, role = MemberRole.CREATOR))
        memberRepository.save(member(id = 2L, role = MemberRole.CLASSMATE))
        val course = courseService.createCourse(createCourseCommand(), 1L)
        courseService.openCourse(CourseStatusCommand(memberId = 1L, courseId = course.courseId))
        val enrollment =
            service.enroll(EnrollCourseCommand(memberId = 2L, courseId = course.courseId))
        service.cancelEnrollment(
            EnrollmentStatusCommand(
                memberId = 2L,
                enrollmentId = enrollment.enrollmentId,
            ),
        )

        val exception = assertThrows(CourseInvalidStateException::class.java) {
            service.cancelEnrollment(
                EnrollmentStatusCommand(
                    memberId = 2L,
                    enrollmentId = enrollment.enrollmentId,
                ),
            )
        }

        assertEquals("이미 취소된 신청입니다.", exception.message)
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
