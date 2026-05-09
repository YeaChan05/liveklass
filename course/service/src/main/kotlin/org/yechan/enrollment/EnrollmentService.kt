package org.yechan.enrollment

import org.springframework.transaction.annotation.Transactional
import org.yechan.course.CourseInvalidStateException
import org.yechan.course.CourseNotFoundException
import org.yechan.course.CourseRepository
import org.yechan.course.CourseStatus
import org.yechan.course.EnrollmentNotFoundException
import org.yechan.member.MemberModel
import org.yechan.member.MemberNotFoundException
import org.yechan.member.MemberRepository
import java.time.Instant

interface EnrollmentUseCase {
    fun enroll(command: EnrollCourseCommand): EnrollmentResult

    fun confirmEnrollment(command: EnrollmentStatusCommand): EnrollmentResult

    fun cancelEnrollment(command: EnrollmentStatusCommand): EnrollmentResult

    fun getMyEnrollments(memberId: Long): List<EnrollmentResult>
}

@Transactional(readOnly = true)
class EnrollmentService(
    private val memberRepository: MemberRepository,
    private val courseRepository: CourseRepository,
    private val enrollmentRepository: EnrollmentRepository,
    private val waitlistRepository: EnrollmentWaitlistRepository,
) : EnrollmentUseCase {
    @Transactional
    override fun enroll(command: EnrollCourseCommand): EnrollmentResult {
        activeMember(command.memberId)
        val course = courseRepository.findById(command.courseId) ?: throw CourseNotFoundException()
        if (course.status == CourseStatus.OPEN && course.seatLeftCount <= 0) {
            waitlistRepository.enqueue(
                courseId = command.courseId,
                memberId = command.memberId,
                requestedAt = Instant.now(),
            )
            throw CourseInvalidStateException("강의 정원을 초과할 수 없습니다.")
        }

        val reservedCourse = course.reserveSeat()
        val savedCourse = courseRepository.save(reservedCourse)
        val enrollment = savedCourse.requestEnrollment(command.memberId)

        return EnrollmentResult.from(enrollmentRepository.save(enrollment, savedCourse.courseId!!))
    }

    @Transactional
    override fun confirmEnrollment(command: EnrollmentStatusCommand): EnrollmentResult {
        val enrollment = ownedEnrollment(command.enrollmentId, command.memberId)
        val confirmed =
            try {
                enrollment.confirm()
            } catch (e: IllegalStateException) {
                throw CourseInvalidStateException(e.message ?: "결제를 확정할 수 없습니다.")
            }

        return EnrollmentResult.from(enrollmentRepository.save(confirmed, enrollment.courseId))
    }

    @Transactional
    override fun cancelEnrollment(command: EnrollmentStatusCommand): EnrollmentResult {
        val enrollment = ownedEnrollment(command.enrollmentId, command.memberId)
        val course =
            courseRepository.findById(enrollment.courseId) ?: throw CourseNotFoundException()
        val cancelled =
            try {
                enrollment.cancel()
            } catch (e: IllegalStateException) {
                throw CourseInvalidStateException(e.message ?: "수강 신청을 취소할 수 없습니다.")
            }
        courseRepository.save(course.releaseSeat())

        return EnrollmentResult.from(enrollmentRepository.save(cancelled, enrollment.courseId))
    }

    override fun getMyEnrollments(memberId: Long): List<EnrollmentResult> {
        activeMember(memberId)
        return enrollmentRepository.findByMemberId(memberId).map(EnrollmentResult::from)
    }

    private fun activeMember(memberId: Long): MemberModel {
        val member = memberRepository.findById(memberId) ?: throw MemberNotFoundException()
        member.validateMemberStatus()
        return member
    }

    private fun ownedEnrollment(
        enrollmentId: Long,
        memberId: Long,
    ) = enrollmentRepository.findById(enrollmentId)
        ?.takeIf { it.memberId == memberId }
        ?: throw EnrollmentNotFoundException()
}
