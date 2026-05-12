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
        val memberId = command.memberId
        val courseId = command.courseId
        if (waitlistRepository.isSoldOut(courseId)) {
            waitlistRepository.enqueue(
                courseId = courseId,
                memberId = memberId,
                requestedAt = Instant.now(),
            )

            throw CourseInvalidStateException("강의 정원을 초과할 수 없습니다.")
        }

        val reserved = courseRepository.reserveSeatIfAvailable(courseId)

        if (!reserved) {
            val course = courseRepository.findById(courseId)
                ?: throw CourseNotFoundException()

            if (course.status != CourseStatus.OPEN) {
                throw CourseInvalidStateException("모집 중인 강의만 신청할 수 있습니다.")
            }

            waitlistRepository.markSoldOut(courseId)

            waitlistRepository.enqueue(
                courseId = courseId,
                memberId = memberId,
                requestedAt = Instant.now(),
            )

            throw CourseInvalidStateException("강의 정원을 초과할 수 없습니다.")
        }

        val enrollment = EnrollmentModelData(
            enrollmentId = null,
            courseId = courseId,
            memberId = memberId,
            status = EnrollmentStatus.PENDING,
        )

        return enrollmentRepository.save(enrollment).toResult()
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

        return enrollmentRepository.save(confirmed).toResult()
    }

    @Transactional
    override fun cancelEnrollment(command: EnrollmentStatusCommand): EnrollmentResult {
        val enrollment = ownedEnrollment(command.enrollmentId, command.memberId)

        val cancelled =
            try {
                enrollment.cancel()
            } catch (e: IllegalStateException) {
                throw CourseInvalidStateException(e.message ?: "수강 신청을 취소할 수 없습니다.")
            }

        val savedEnrollment = enrollmentRepository.save(cancelled)

        val released = courseRepository.releaseSeatIfPossible(enrollment.courseId)

        if (!released) {
            throw CourseInvalidStateException("좌석을 반환할 수 없습니다.")
        }

        waitlistRepository.clearSoldOut(enrollment.courseId)

        return savedEnrollment.toResult()
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
