package org.yechan.enrollment

import java.time.Instant

interface EnrollmentUseCase {
    fun enroll(command: EnrollCourseCommand): EnrollResult

    fun confirmEnrollment(command: EnrollmentStatusCommand): EnrollmentInfo

    fun cancelEnrollment(command: EnrollmentStatusCommand): EnrollmentInfo

    fun cancelWaitlist(command: EnrollmentWaitlistCommand)

    fun getMyEnrollments(memberId: Long): List<EnrollmentInfo>

    fun getMyWaitlist(memberId: Long): List<WaitlistInfo>
}

class EnrollmentService(
    private val enrollmentTransactionService: EnrollmentTransactionService,
    private val waitlistRepository: EnrollmentWaitlistRepository,
) : EnrollmentUseCase {
    override fun enroll(command: EnrollCourseCommand): EnrollResult {
        val memberId = command.memberId
        val courseId = command.courseId

        enrollmentTransactionService.findPendingOrConfirmedEnrollment(command)?.let {
            return EnrollResult.Enrolled(it)
        }

        if (waitlistRepository.isSoldOut(courseId)) {
            enqueueWaitlist(courseId, memberId)
            return EnrollResult.Waitlisted(
                courseId = courseId,
                memberId = memberId,
            )
        }

        return when (val result = enrollmentTransactionService.enroll(command)) {
            is EnrollmentEnrollTransactionResult.Enrolled -> EnrollResult.Enrolled(result.enrollment)
            EnrollmentEnrollTransactionResult.SoldOut -> rejectSoldOut(courseId, memberId)
        }
    }

    override fun confirmEnrollment(command: EnrollmentStatusCommand): EnrollmentInfo = enrollmentTransactionService.confirmEnrollment(command)

    override fun cancelEnrollment(command: EnrollmentStatusCommand): EnrollmentInfo {
        val result = enrollmentTransactionService.cancelEnrollment(command)
        waitlistRepository.clearSoldOut(result.courseId)
        return result.enrollment
    }

    override fun cancelWaitlist(command: EnrollmentWaitlistCommand) {
        waitlistRepository.remove(command.courseId, command.memberId)
    }

    override fun getMyEnrollments(memberId: Long): List<EnrollmentInfo> = enrollmentTransactionService.getMyEnrollments(memberId)

    override fun getMyWaitlist(memberId: Long): List<WaitlistInfo> = waitlistRepository.findByMemberId(memberId)
        .map {
            WaitlistInfo(
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
    ): EnrollResult.Waitlisted {
        waitlistRepository.markSoldOut(courseId)
        try {
            enrollmentTransactionService.requireOpenCourse(courseId)
        } catch (e: RuntimeException) {
            waitlistRepository.clearSoldOut(courseId)
            throw e
        }

        enqueueWaitlist(courseId, memberId)
        return EnrollResult.Waitlisted(
            courseId = courseId,
            memberId = memberId,
        )
    }
}

sealed interface EnrollResult {
    data class Enrolled(
        val enrollment: EnrollmentInfo,
    ) : EnrollResult

    data class Waitlisted(
        val courseId: Long,
        val memberId: Long,
    ) : EnrollResult
}

data class WaitlistInfo(
    val courseId: Long,
    val memberId: Long,
    val requestedAt: Instant,
)

val EnrollResult.enrollment: EnrollmentInfo
    get() = when (this) {
        is EnrollResult.Enrolled -> enrollment
        is EnrollResult.Waitlisted -> error("대기열 등록 결과에는 수강 신청 정보가 없습니다.")
    }
