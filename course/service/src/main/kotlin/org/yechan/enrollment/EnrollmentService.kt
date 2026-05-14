package org.yechan.enrollment

import org.springframework.transaction.annotation.Transactional
import java.time.Instant

sealed interface EnrollmentEnrollResult {
    data class Enrolled(
        val enrollment: EnrollmentResult,
    ) : EnrollmentEnrollResult

    data class Waitlisted(
        val courseId: Long,
        val memberId: Long,
    ) : EnrollmentEnrollResult
}

data class EnrollmentWaitlistResult(
    val courseId: Long,
    val memberId: Long,
    val requestedAt: Instant,
)

open class EnrollmentService(
    private val enrollmentTransactionService: EnrollmentTransactionService,
    private val enrollmentRepository: EnrollmentRepository,
    private val waitlistRepository: EnrollmentWaitlistRepository,
) : EnrollmentUseCase {
    override fun enroll(command: EnrollCourseCommand): EnrollmentEnrollResult {
        val memberId = command.memberId
        val courseId = command.courseId

        if (waitlistRepository.isSoldOut(courseId)) {
            enqueueWaitlist(courseId, memberId)
            return EnrollmentEnrollResult.Waitlisted(
                courseId = courseId,
                memberId = memberId,
            )
        }

        return when (val result = enrollmentTransactionService.enroll(command)) {
            is EnrollmentEnrollTransactionResult.Enrolled -> EnrollmentEnrollResult.Enrolled(result.enrollment)

            EnrollmentEnrollTransactionResult.SoldOut -> {
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

    override fun cancelWaitlist(command: EnrollmentWaitlistCommand) {
        waitlistRepository.remove(command.courseId, command.memberId)
    }

    @Transactional(readOnly = true)
    override fun getMyEnrollments(memberId: Long): List<EnrollmentResult> = enrollmentRepository.findByMemberId(memberId).map { it.toResult() }

    @Transactional(readOnly = true)
    override fun getMyWaitlist(memberId: Long): List<EnrollmentWaitlistResult> = waitlistRepository.findByMemberId(memberId)
        .map {
            EnrollmentWaitlistResult(
                courseId = it.courseId,
                memberId = it.memberId,
                requestedAt = it.requestedAt,
            )
        }

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
    ): EnrollmentEnrollResult.Waitlisted {
        waitlistRepository.markSoldOut(courseId)
        try {
            enrollmentTransactionService.requireOpenCourse(courseId)
        } catch (e: RuntimeException) {
            waitlistRepository.clearSoldOut(courseId)
            throw e
        }

        enqueueWaitlist(courseId, memberId)
        return EnrollmentEnrollResult.Waitlisted(
            courseId = courseId,
            memberId = memberId,
        )
    }
}

val EnrollmentEnrollResult.enrollment: EnrollmentResult
    get() = when (this) {
        is EnrollmentEnrollResult.Enrolled -> enrollment
        is EnrollmentEnrollResult.Waitlisted -> error("대기열 등록 결과에는 수강 신청 정보가 없습니다.")
    }
