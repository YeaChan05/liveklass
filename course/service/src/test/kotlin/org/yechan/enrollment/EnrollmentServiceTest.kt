package org.yechan.enrollment

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
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
import java.time.Duration
import java.time.Instant
import java.time.LocalDateTime

class EnrollmentServiceTest {
    private val memberRepository = FakeMemberRepository()
    private val courseRepository = FakeCourseRepository()
    private val enrollmentRepository = FakeEnrollmentRepository()
    private val waitlistRepository = FakeEnrollmentWaitlistRepository()
    private val courseService = CourseService(memberRepository, courseRepository)
    private val enrollmentTransactionService =
        EnrollmentTransactionService(courseRepository, enrollmentRepository)
    private val service =
        EnrollmentService(
            enrollmentTransactionService,
            waitlistRepository,
        )

    @Test
    fun `클래스메이트는 모집 중인 강의를 신청하면 결제 대기 신청이 생성되고 좌석이 감소한다`() {
        memberRepository.save(member(id = 1L, role = MemberRole.CREATOR))
        memberRepository.save(member(id = 2L, role = MemberRole.CLASSMATE))
        val course = courseService.createCourse(createCourseCommand(), 1L)
        courseService.openCourse(CourseStatusCommand(memberId = 1L, courseId = course.courseId))

        val enrollment =
            service.enroll(
                EnrollCourseCommand(
                    memberId = 2L,
                    courseId = course.courseId,
                ),
            ).enrollment
        val changedCourse = courseService.getCourse(course.courseId)

        assertThat(enrollment.status).isEqualTo(EnrollmentStatus.PENDING)
        assertThat(changedCourse.seatLeftCount).isEqualTo(1)
    }

    @Test
    fun `매진 표시 전 신규 수강 신청은 기존 신청을 한 번만 조회한다`() {
        memberRepository.save(member(id = 1L, role = MemberRole.CREATOR))
        memberRepository.save(member(id = 2L, role = MemberRole.CLASSMATE))
        val course = courseService.createCourse(createCourseCommand(), 1L)
        courseService.openCourse(CourseStatusCommand(memberId = 1L, courseId = course.courseId))

        service.enroll(
            EnrollCourseCommand(
                memberId = 2L,
                courseId = course.courseId,
            ),
        )

        assertThat(enrollmentRepository.findByMemberIdAndCourseIdCallCount).isEqualTo(1)
    }

    @Test
    fun `남은 좌석이 없으면 수강 신청은 대기열에 등록된다`() {
        memberRepository.save(member(id = 1L, role = MemberRole.CREATOR))
        memberRepository.save(member(id = 2L, role = MemberRole.CLASSMATE))
        memberRepository.save(member(id = 3L, role = MemberRole.CLASSMATE))
        val course = courseService.createCourse(createCourseCommand(capacity = 1), 1L)
        courseService.openCourse(CourseStatusCommand(memberId = 1L, courseId = course.courseId))
        service.enroll(EnrollCourseCommand(memberId = 2L, courseId = course.courseId))

        val waitlisted =
            service.enroll(EnrollCourseCommand(memberId = 3L, courseId = course.courseId))

        assertThat(waitlisted).isEqualTo(
            EnrollResult.Waitlisted(
                courseId = course.courseId,
                memberId = 3L,
            ),
        )

        assertThat(waitlistRepository.isSoldOut(course.courseId)).isTrue()
        assertThat(waitlistRepository.pop(course.courseId)?.memberId).isEqualTo(3L)
        assertThat(waitlistRepository.isSoldOut(course.courseId)).isFalse()
    }

    @Test
    fun `만료된 신청은 같은 수강 신청 row를 다시 사용한다`() {
        memberRepository.save(member(id = 1L, role = MemberRole.CREATOR))
        memberRepository.save(member(id = 2L, role = MemberRole.CLASSMATE))
        val course = courseService.createCourse(createCourseCommand(), 1L)
        courseService.openCourse(CourseStatusCommand(memberId = 1L, courseId = course.courseId))

        val expired = enrollmentRepository.save(
            EnrollmentModelData(
                courseId = course.courseId,
                memberId = 2L,
                status = EnrollmentStatus.EXPIRED,
            ),
        )

        val reenrolled =
            service.enroll(
                EnrollCourseCommand(
                    memberId = 2L,
                    courseId = course.courseId,
                ),
            ).enrollment

        assertThat(reenrolled.enrollmentId).isEqualTo(expired.enrollmentId)
        assertThat(reenrolled.status).isEqualTo(EnrollmentStatus.PENDING)
        assertThat(courseService.getCourse(course.courseId).seatLeftCount).isEqualTo(1)
    }

    @Test
    fun `내 수강 신청 목록을 조회한다`() {
        memberRepository.save(member(id = 1L, role = MemberRole.CREATOR))
        memberRepository.save(member(id = 2L, role = MemberRole.CLASSMATE))
        val course = courseService.createCourse(createCourseCommand(), 1L)
        courseService.openCourse(CourseStatusCommand(memberId = 1L, courseId = course.courseId))
        service.enroll(EnrollCourseCommand(memberId = 2L, courseId = course.courseId))

        val enrollments = service.getMyEnrollments(2L)

        assertThat(enrollments.size).isEqualTo(1)
        assertThat(enrollments.single().memberId).isEqualTo(2L)
    }

    @Test
    fun `CREATOR는 수강 신청 확정 목록 조회를 할 수 있다`() {
        memberRepository.save(member(id = 1L, role = MemberRole.CREATOR))
        val course = courseService.createCourse(createCourseCommand(), 1L)
        courseService.openCourse(CourseStatusCommand(memberId = 1L, courseId = course.courseId))

        val enrollment =
            service.enroll(
                EnrollCourseCommand(
                    memberId = 1L,
                    courseId = course.courseId,
                ),
            ).enrollment

        val confirmed =
            service.confirmEnrollment(EnrollmentStatusCommand(1L, enrollment.enrollmentId))

        val enrollments = service.getMyEnrollments(1L)

        assertThat(enrollment.status).isEqualTo(EnrollmentStatus.PENDING)
        assertThat(confirmed.status).isEqualTo(EnrollmentStatus.CONFIRMED)
        assertThat(enrollments).hasSize(1)
        assertThat(enrollments.single().status).isEqualTo(EnrollmentStatus.CONFIRMED)
    }

    @Test
    fun `CREATOR는 결제 대기 상태의 본인 수강 신청을 취소할 수 있다`() {
        memberRepository.save(member(id = 1L, role = MemberRole.CREATOR))
        val course = courseService.createCourse(createCourseCommand(), 1L)
        courseService.openCourse(CourseStatusCommand(memberId = 1L, courseId = course.courseId))

        val enrollment =
            service.enroll(
                EnrollCourseCommand(
                    memberId = 1L,
                    courseId = course.courseId,
                ),
            ).enrollment

        val cancelled =
            service.cancelEnrollment(EnrollmentStatusCommand(1L, enrollment.enrollmentId))

        val changedCourse = courseService.getCourse(course.courseId)

        assertThat(enrollment.status).isEqualTo(EnrollmentStatus.PENDING)
        assertThat(cancelled.status).isEqualTo(EnrollmentStatus.CANCELLED)
        assertThat(changedCourse.seatLeftCount).isEqualTo(2)
    }

    @Test
    fun `존재하지 않는 강의는 수강 신청할 수 없다`() {
        memberRepository.save(member(id = 2L, role = MemberRole.CLASSMATE))

        assertThatThrownBy {
            service.enroll(EnrollCourseCommand(memberId = 2L, courseId = 999L))
        }
            .isInstanceOf(CourseNotFoundException::class.java)
    }

    @Test
    fun `다른 회원의 수강 신청은 확정할 수 없다`() {
        memberRepository.save(member(id = 1L, role = MemberRole.CREATOR))
        memberRepository.save(member(id = 2L, role = MemberRole.CLASSMATE))
        memberRepository.save(member(id = 3L, role = MemberRole.CLASSMATE))
        val course = courseService.createCourse(createCourseCommand(), 1L)
        courseService.openCourse(CourseStatusCommand(memberId = 1L, courseId = course.courseId))
        val enrollment =
            service.enroll(
                EnrollCourseCommand(
                    memberId = 2L,
                    courseId = course.courseId,
                ),
            ).enrollment

        assertThatThrownBy {
            service.confirmEnrollment(
                EnrollmentStatusCommand(
                    memberId = 3L,
                    enrollmentId = enrollment.enrollmentId,
                ),
            )
        }
            .isInstanceOf(EnrollmentNotFoundException::class.java)
    }

    @Test
    fun `확정된 신청은 다시 확정할 수 없다`() {
        memberRepository.save(member(id = 1L, role = MemberRole.CREATOR))
        memberRepository.save(member(id = 2L, role = MemberRole.CLASSMATE))
        val course = courseService.createCourse(createCourseCommand(), 1L)
        courseService.openCourse(CourseStatusCommand(memberId = 1L, courseId = course.courseId))
        val enrollment =
            service.enroll(
                EnrollCourseCommand(
                    memberId = 2L,
                    courseId = course.courseId,
                ),
            ).enrollment
        service.confirmEnrollment(
            EnrollmentStatusCommand(
                memberId = 2L,
                enrollmentId = enrollment.enrollmentId,
            ),
        )

        assertThatThrownBy {
            service.confirmEnrollment(
                EnrollmentStatusCommand(
                    memberId = 2L,
                    enrollmentId = enrollment.enrollmentId,
                ),
            )
        }
            .isInstanceOf(CourseInvalidStateException::class.java)
            .hasMessage("결제 대기 상태의 신청만 확정할 수 있습니다.")
    }

    @Test
    fun `취소된 신청은 다시 취소할 수 없다`() {
        memberRepository.save(member(id = 1L, role = MemberRole.CREATOR))
        memberRepository.save(member(id = 2L, role = MemberRole.CLASSMATE))
        val course = courseService.createCourse(createCourseCommand(), 1L)
        courseService.openCourse(CourseStatusCommand(memberId = 1L, courseId = course.courseId))
        val enrollment =
            service.enroll(
                EnrollCourseCommand(
                    memberId = 2L,
                    courseId = course.courseId,
                ),
            ).enrollment
        service.cancelEnrollment(
            EnrollmentStatusCommand(
                memberId = 2L,
                enrollmentId = enrollment.enrollmentId,
            ),
        )

        assertThatThrownBy {
            service.cancelEnrollment(
                EnrollmentStatusCommand(
                    memberId = 2L,
                    enrollmentId = enrollment.enrollmentId,
                ),
            )
        }
            .isInstanceOf(CourseInvalidStateException::class.java)
            .hasMessage("결제 대기 상태에서만 취소가 가능합니다.")
    }

    @Test
    fun `모집 전 강의는 수강 신청할 수 없다`() {
        memberRepository.save(member(id = 1L, role = MemberRole.CREATOR))
        memberRepository.save(member(id = 2L, role = MemberRole.CLASSMATE))
        val course = courseService.createCourse(createCourseCommand(), 1L)

        assertThatThrownBy {
            service.enroll(EnrollCourseCommand(memberId = 2L, courseId = course.courseId))
        }
            .isInstanceOf(CourseInvalidStateException::class.java)
            .hasMessage("모집중인 강의가 아닙니다.")
    }

    @Test
    fun `마감된 강의는 수강 신청할 수 없다`() {
        memberRepository.save(member(id = 1L, role = MemberRole.CREATOR))
        memberRepository.save(member(id = 2L, role = MemberRole.CLASSMATE))
        val course = courseService.createCourse(createCourseCommand(), 1L)
        courseService.openCourse(CourseStatusCommand(memberId = 1L, courseId = course.courseId))
        courseService.closeCourse(CourseStatusCommand(memberId = 1L, courseId = course.courseId))

        assertThatThrownBy {
            service.enroll(EnrollCourseCommand(memberId = 2L, courseId = course.courseId))
        }
            .isInstanceOf(CourseInvalidStateException::class.java)
            .hasMessage("모집중인 강의가 아닙니다.")
    }

    @Test
    fun `존재하지 않는 수강 신청은 확정할 수 없다`() {
        assertThatThrownBy {
            service.confirmEnrollment(
                EnrollmentStatusCommand(
                    memberId = 1L,
                    enrollmentId = 999L,
                ),
            )
        }
            .isInstanceOf(EnrollmentNotFoundException::class.java)
    }

    @Test
    fun `취소된 신청은 결제 확정할 수 없다`() {
        memberRepository.save(member(id = 1L, role = MemberRole.CREATOR))
        memberRepository.save(member(id = 2L, role = MemberRole.CLASSMATE))
        val course = courseService.createCourse(createCourseCommand(), 1L)
        courseService.openCourse(CourseStatusCommand(memberId = 1L, courseId = course.courseId))
        val enrollment = service.enroll(
            EnrollCourseCommand(
                memberId = 2L,
                courseId = course.courseId,
            ),
        ).enrollment

        service.cancelEnrollment(
            EnrollmentStatusCommand(
                memberId = 2L,
                enrollmentId = enrollment.enrollmentId,
            ),
        )

        assertThatThrownBy {
            service.confirmEnrollment(
                EnrollmentStatusCommand(
                    memberId = 2L,
                    enrollmentId = enrollment.enrollmentId,
                ),
            )
        }
            .isInstanceOf(CourseInvalidStateException::class.java)
            .hasMessage("결제 대기 상태의 신청만 확정할 수 있습니다.")
    }

    @Test
    fun `결제 대기 신청을 취소하면 신청 상태가 취소되고 좌석이 반환된다`() {
        memberRepository.save(member(id = 1L, role = MemberRole.CREATOR))
        memberRepository.save(member(id = 2L, role = MemberRole.CLASSMATE))
        val course = courseService.createCourse(createCourseCommand(), 1L)
        courseService.openCourse(CourseStatusCommand(memberId = 1L, courseId = course.courseId))

        val enrollment =
            service.enroll(
                EnrollCourseCommand(
                    memberId = 2L,
                    courseId = course.courseId,
                ),
            ).enrollment

        val beforeCancelCourse = courseService.getCourse(course.courseId)

        val cancelled =
            service.cancelEnrollment(EnrollmentStatusCommand(2L, enrollment.enrollmentId))

        val afterCancelCourse = courseService.getCourse(course.courseId)

        assertThat(enrollment.status).isEqualTo(EnrollmentStatus.PENDING)
        assertThat(beforeCancelCourse.seatLeftCount).isEqualTo(1)
        assertThat(cancelled.status).isEqualTo(EnrollmentStatus.CANCELLED)
        assertThat(afterCancelCourse.seatLeftCount).isEqualTo(2)
    }

    @Test
    fun `확정된 신청은 취소할 수 없다`() {
        memberRepository.save(member(id = 1L, role = MemberRole.CREATOR))
        memberRepository.save(member(id = 2L, role = MemberRole.CLASSMATE))
        val course = courseService.createCourse(createCourseCommand(), 1L)
        courseService.openCourse(CourseStatusCommand(memberId = 1L, courseId = course.courseId))

        val enrollment =
            service.enroll(
                EnrollCourseCommand(
                    memberId = 2L,
                    courseId = course.courseId,
                ),
            ).enrollment

        val confirmed =
            service.confirmEnrollment(EnrollmentStatusCommand(2L, enrollment.enrollmentId))

        val beforeCancelCourse = courseService.getCourse(course.courseId)

        assertThatThrownBy {
            service.cancelEnrollment(EnrollmentStatusCommand(2L, enrollment.enrollmentId))
        }
            .isInstanceOf(CourseInvalidStateException::class.java)
            .hasMessage("결제 대기 상태에서만 취소가 가능합니다.")

        val afterCancelCourse = courseService.getCourse(course.courseId)

        assertThat(confirmed.status).isEqualTo(EnrollmentStatus.CONFIRMED)
        assertThat(beforeCancelCourse.seatLeftCount).isEqualTo(1)
        assertThat(afterCancelCourse.seatLeftCount).isEqualTo(1)
    }

    @Test
    fun `다른 회원의 수강 신청은 취소할 수 없다`() {
        memberRepository.save(member(id = 1L, role = MemberRole.CREATOR))
        memberRepository.save(member(id = 2L, role = MemberRole.CLASSMATE))
        memberRepository.save(member(id = 3L, role = MemberRole.CLASSMATE))
        val course = courseService.createCourse(createCourseCommand(), 1L)
        courseService.openCourse(CourseStatusCommand(memberId = 1L, courseId = course.courseId))
        val enrollment = service.enroll(
            EnrollCourseCommand(
                memberId = 2L,
                courseId = course.courseId,
            ),
        ).enrollment

        assertThatThrownBy {
            service.cancelEnrollment(
                EnrollmentStatusCommand(
                    memberId = 3L,
                    enrollmentId = enrollment.enrollmentId,
                ),
            )
        }
            .isInstanceOf(EnrollmentNotFoundException::class.java)

        val changedCourse = courseService.getCourse(course.courseId)

        assertThat(changedCourse.seatLeftCount).isEqualTo(1)
    }

    @Test
    fun `존재하지 않는 수강 신청은 취소할 수 없다`() {
        assertThatThrownBy {
            service.cancelEnrollment(
                EnrollmentStatusCommand(
                    memberId = 1L,
                    enrollmentId = 999L,
                ),
            )
        }
            .isInstanceOf(EnrollmentNotFoundException::class.java)
    }

    @Test
    fun `수강 취소로 좌석이 반환되면 매진 표시가 해제된다`() {
        memberRepository.save(member(id = 1L, role = MemberRole.CREATOR))
        memberRepository.save(member(id = 2L, role = MemberRole.CLASSMATE))
        memberRepository.save(member(id = 3L, role = MemberRole.CLASSMATE))
        val course = courseService.createCourse(createCourseCommand(capacity = 1), 1L)
        courseService.openCourse(CourseStatusCommand(memberId = 1L, courseId = course.courseId))

        val enrollment = service.enroll(
            EnrollCourseCommand(
                memberId = 2L,
                courseId = course.courseId,
            ),
        ).enrollment

        val waitlisted =
            service.enroll(
                EnrollCourseCommand(
                    memberId = 3L,
                    courseId = course.courseId,
                ),
            )

        assertThat(waitlisted).isEqualTo(
            EnrollResult.Waitlisted(
                courseId = course.courseId,
                memberId = 3L,
            ),
        )

        assertThat(waitlistRepository.isSoldOut(course.courseId)).isTrue()

        service.cancelEnrollment(
            EnrollmentStatusCommand(
                memberId = 2L,
                enrollmentId = enrollment.enrollmentId,
            ),
        )

        assertThat(waitlistRepository.isSoldOut(course.courseId)).isFalse()
    }

    @Test
    fun `이미 매진 표시된 강의는 좌석 확보를 시도하지 않고 대기열에 등록한다`() {
        memberRepository.save(member(id = 1L, role = MemberRole.CREATOR))
        memberRepository.save(member(id = 2L, role = MemberRole.CLASSMATE))
        val course = courseService.createCourse(createCourseCommand(capacity = 2), 1L)
        courseService.openCourse(CourseStatusCommand(memberId = 1L, courseId = course.courseId))

        waitlistRepository.markSoldOut(course.courseId)

        val waitlisted =
            service.enroll(
                EnrollCourseCommand(
                    memberId = 2L,
                    courseId = course.courseId,
                ),
            )

        assertThat(waitlisted).isEqualTo(
            EnrollResult.Waitlisted(
                courseId = course.courseId,
                memberId = 2L,
            ),
        )

        val changedCourse = courseService.getCourse(course.courseId)
        val popped = waitlistRepository.pop(course.courseId)
        val enrollments = service.getMyEnrollments(2L)

        assertThat(changedCourse.seatLeftCount).isEqualTo(2)
        assertThat(popped?.memberId).isEqualTo(2L)
        assertThat(enrollments).isEmpty()
        assertThat(waitlistRepository.isSoldOut(course.courseId)).isFalse()
    }

    @Test
    fun `이미 신청한 클래스메이트는 매진 표시된 강의를 다시 신청해도 대기열에 등록되지 않는다`() {
        memberRepository.save(member(id = 1L, role = MemberRole.CREATOR))
        memberRepository.save(member(id = 2L, role = MemberRole.CLASSMATE))
        val course = courseService.createCourse(createCourseCommand(capacity = 1), 1L)
        courseService.openCourse(CourseStatusCommand(memberId = 1L, courseId = course.courseId))
        val enrolled =
            service.enroll(
                EnrollCourseCommand(
                    memberId = 2L,
                    courseId = course.courseId,
                ),
            ).enrollment

        waitlistRepository.markSoldOut(course.courseId)

        val result =
            service.enroll(
                EnrollCourseCommand(
                    memberId = 2L,
                    courseId = course.courseId,
                ),
            )

        assertThat(result).isEqualTo(EnrollResult.Enrolled(enrolled))
        assertThat(waitlistRepository.findByMemberId(2L)).isEmpty()
    }

    @Test
    fun `내 대기열을 조회한다`() {
        memberRepository.save(member(id = 1L, role = MemberRole.CREATOR))
        memberRepository.save(member(id = 2L, role = MemberRole.CLASSMATE))
        memberRepository.save(member(id = 3L, role = MemberRole.CLASSMATE))
        val course = courseService.createCourse(createCourseCommand(capacity = 1), 1L)
        courseService.openCourse(CourseStatusCommand(memberId = 1L, courseId = course.courseId))

        waitlistRepository.enqueue(course.courseId, 2L, Instant.parse("2026-01-01T00:00:00Z"))
        waitlistRepository.enqueue(course.courseId, 3L, Instant.parse("2026-01-01T00:00:01Z"))

        val waitlist = service.getMyWaitlist(2L)

        assertThat(waitlist).hasSize(1)
        assertThat(waitlist.single().courseId).isEqualTo(course.courseId)
        assertThat(waitlist.single().memberId).isEqualTo(2L)
        assertThat(waitlist.single().requestedAt).isEqualTo(Instant.parse("2026-01-01T00:00:00Z"))
    }

    @Test
    fun `대기열 신청을 취소하면 마지막 대기열일 때 매진 표시가 해제된다`() {
        memberRepository.save(member(id = 1L, role = MemberRole.CREATOR))
        memberRepository.save(member(id = 2L, role = MemberRole.CLASSMATE))
        val course = courseService.createCourse(createCourseCommand(capacity = 1), 1L)
        courseService.openCourse(CourseStatusCommand(memberId = 1L, courseId = course.courseId))

        waitlistRepository.markSoldOut(course.courseId)
        waitlistRepository.enqueue(course.courseId, 2L, Instant.parse("2026-01-01T00:00:00Z"))

        service.cancelWaitlist(
            EnrollmentWaitlistCommand(
                memberId = 2L,
                courseId = course.courseId,
            ),
        )

        assertThat(waitlistRepository.findByMemberId(2L)).isEmpty()
        assertThat(waitlistRepository.isSoldOut(course.courseId)).isFalse()
    }

    @Test
    fun `결제 대기 신청은 결제 확정할 수 있다`() {
        memberRepository.save(member(id = 1L, role = MemberRole.CREATOR))
        memberRepository.save(member(id = 2L, role = MemberRole.CLASSMATE))
        val course = courseService.createCourse(createCourseCommand(capacity = 2), 1L)
        courseService.openCourse(CourseStatusCommand(memberId = 1L, courseId = course.courseId))

        val enrollment = service.enroll(
            EnrollCourseCommand(
                memberId = 2L,
                courseId = course.courseId,
            ),
        ).enrollment

        val confirmed = service.confirmEnrollment(
            EnrollmentStatusCommand(
                memberId = 2L,
                enrollmentId = enrollment.enrollmentId,
            ),
        )

        val changedCourse = courseService.getCourse(course.courseId)

        assertThat(enrollment.status).isEqualTo(EnrollmentStatus.PENDING)
        assertThat(confirmed.status).isEqualTo(EnrollmentStatus.CONFIRMED)
        assertThat(changedCourse.seatLeftCount).isEqualTo(1)
    }

    @Test
    fun `결제 대기 만료 시간은 설정값을 따른다`() {
        memberRepository.save(member(id = 1L, role = MemberRole.CREATOR))
        memberRepository.save(member(id = 2L, role = MemberRole.CLASSMATE))
        val course = courseService.createCourse(createCourseCommand(capacity = 2), 1L)
        courseService.openCourse(CourseStatusCommand(memberId = 1L, courseId = course.courseId))
        val customTransactionService = EnrollmentTransactionService(
            courseRepository = courseRepository,
            enrollmentRepository = enrollmentRepository,
            paymentPendingExpiresIn = Duration.ofMinutes(3),
        )

        val enrolled = customTransactionService.enroll(
            EnrollCourseCommand(memberId = 2L, courseId = course.courseId),
        )

        val result = (enrolled as EnrollmentEnrollTransactionResult.Enrolled).enrollment
        val saved =
            enrollmentRepository.findById(result.enrollmentId) ?: error("missing enrollment")

        assertThat(Duration.between(saved.paymentPendingStartedAt, saved.paymentPendingExpiresAt))
            .isEqualTo(Duration.ofMinutes(3))
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
