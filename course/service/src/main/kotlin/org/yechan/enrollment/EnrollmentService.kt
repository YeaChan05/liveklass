package org.yechan.enrollment

import org.springframework.transaction.annotation.Transactional
import org.yechan.course.CourseInvalidStateException
import org.yechan.course.CourseNotFoundException
import org.yechan.course.CourseRepository
import org.yechan.course.CourseStatus
import org.yechan.course.EnrollmentNotFoundException
import java.time.Instant

interface EnrollmentUseCase {
    fun enroll(command: EnrollCourseCommand): EnrollmentResult

    fun confirmEnrollment(command: EnrollmentStatusCommand): EnrollmentResult

    fun cancelEnrollment(command: EnrollmentStatusCommand): EnrollmentResult

    fun getMyEnrollments(memberId: Long): List<EnrollmentResult>
}

@Transactional(readOnly = true)
class EnrollmentService(
    private val courseRepository: CourseRepository,
    private val enrollmentRepository: EnrollmentRepository,
    private val waitlistRepository: EnrollmentWaitlistRepository,
) : EnrollmentUseCase {
    @Transactional
    override fun enroll(command: EnrollCourseCommand): EnrollmentResult {
        val course = courseRepository.findByIdForUpdate(command.courseId)
            ?: throw CourseNotFoundException()

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

        return enrollmentRepository.save(enrollment, savedCourse.courseId!!).toResult()
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

        return enrollmentRepository.save(confirmed, enrollment.courseId).toResult()
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

        return enrollmentRepository.save(cancelled, enrollment.courseId).toResult()
    }

    override fun getMyEnrollments(memberId: Long): List<EnrollmentResult> = enrollmentRepository.findByMemberId(memberId).map { it.toResult() }

    private fun ownedEnrollment(
        enrollmentId: Long,
        memberId: Long,
    ) = enrollmentRepository.findById(enrollmentId)
        ?.takeIf { it.memberId == memberId }
        ?: throw EnrollmentNotFoundException()
}

private fun EnrollmentModel.toResult(): EnrollmentResult = EnrollmentResult(
    enrollmentId = requireNotNull(enrollmentId),
    courseId = courseId,
    memberId = memberId,
    status = status,
)
