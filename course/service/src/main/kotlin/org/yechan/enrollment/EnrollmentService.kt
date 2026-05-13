package org.yechan.enrollment

import org.springframework.transaction.annotation.Transactional
import org.yechan.course.CourseInvalidStateException
import java.time.Instant

open class EnrollmentService(
    private val enrollmentTransactionService: EnrollmentTransactionService,
    private val enrollmentRepository: EnrollmentRepository,
    private val waitlistRepository: EnrollmentWaitlistRepository,
) : EnrollmentUseCase {
    override fun enroll(command: EnrollCourseCommand): EnrollmentResult {
        val memberId = command.memberId
        val courseId = command.courseId

        if (waitlistRepository.isSoldOut(courseId)) {
            enqueueWaitlist(courseId, memberId)
            throw CourseInvalidStateException("강의 정원을 초과할 수 없습니다.")
        }

        return when (val result = enrollmentTransactionService.enroll(command)) {
            is EnrollmentEnrollResult.Enrolled -> result.enrollment

            EnrollmentEnrollResult.SoldOut -> {
                rejectSoldOut(courseId, memberId)
            }
        }
    }

    override fun confirmEnrollment(command: EnrollmentStatusCommand): EnrollmentResult = enrollmentTransactionService.confirmEnrollment(command)

    override fun cancelEnrollment(command: EnrollmentStatusCommand): EnrollmentResult {
        val result = enrollmentTransactionService.cancelEnrollment(command)
        waitlistRepository.clearSoldOut(result.courseId)
        return result.enrollment
    }

    @Transactional(readOnly = true)
    override fun getMyEnrollments(memberId: Long): List<EnrollmentResult> = enrollmentRepository.findByMemberId(memberId).map { it.toResult() }

    private fun enqueueWaitlist(
        courseId: Long,
        memberId: Long,
    ) {
        waitlistRepository.enqueue(
            courseId = courseId,
            memberId = memberId,
            requestedAt = Instant.now(),
        )
    }

    private fun rejectSoldOut(
        courseId: Long,
        memberId: Long,
    ): Nothing {
        waitlistRepository.markSoldOut(courseId)
        try {
            enrollmentTransactionService.requireOpenCourse(courseId)
        } catch (e: RuntimeException) {
            waitlistRepository.clearSoldOut(courseId)
            throw e
        }

        enqueueWaitlist(courseId, memberId)
        throw CourseInvalidStateException("강의 정원을 초과할 수 없습니다.")
    }
}
