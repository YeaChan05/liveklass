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
    private val waitlistCoordinator: EnrollmentWaitlistCoordinator,
) : EnrollmentUseCase {
    override fun enroll(command: EnrollCourseCommand): EnrollResult {
        val memberId = command.memberId
        val courseId = command.courseId

        if (waitlistCoordinator.isWaitlistMode(courseId)) {
            enrollmentTransactionService.findSeatOccupyingEnrollment(command)?.let {
                return EnrollResult.Enrolled(it)
            }

            joinWaitlist(courseId, memberId)
            return EnrollResult.Waitlisted(
                courseId = courseId,
                memberId = memberId,
            )
        }

        return when (val result = enrollmentTransactionService.enroll(command)) {
            is EnrollmentEnrollTransactionResult.Enrolled -> EnrollResult.Enrolled(result.enrollment)
            EnrollmentEnrollTransactionResult.SoldOut -> joinWaitlistAfterSeatReservationFailed(courseId, memberId)
        }
    }

    override fun confirmEnrollment(command: EnrollmentStatusCommand): EnrollmentInfo = enrollmentTransactionService.confirmEnrollment(command)

    override fun cancelEnrollment(command: EnrollmentStatusCommand): EnrollmentInfo {
        val result = enrollmentTransactionService.cancelEnrollment(command)
        waitlistCoordinator.promoteAfterSeatRelease(result.courseId)
        return result.enrollment
    }

    override fun cancelWaitlist(command: EnrollmentWaitlistCommand) {
        waitlistCoordinator.cancelWaitlist(command.courseId, command.memberId)
    }

    override fun getMyEnrollments(memberId: Long): List<EnrollmentInfo> = enrollmentTransactionService.getMyEnrollments(memberId)

    override fun getMyWaitlist(memberId: Long): List<WaitlistInfo> = waitlistCoordinator.findByMemberId(memberId)
        .map {
            WaitlistInfo(
                courseId = it.courseId,
                memberId = it.memberId,
                requestedAt = it.requestedAt,
            )
        }

    private fun joinWaitlist(
        courseId: Long,
        memberId: Long,
    ) {
        waitlistCoordinator.joinWaitlist(
            courseId = courseId,
            memberId = memberId,
            requestedAt = Instant.now(),
        )
    }

    private fun joinWaitlistAfterSeatReservationFailed(
        courseId: Long,
        memberId: Long,
    ): EnrollResult.Waitlisted {
        waitlistCoordinator.enableWaitlistMode(courseId)
        try {
            enrollmentTransactionService.requireOpenCourse(courseId)
        } catch (e: RuntimeException) {
            waitlistCoordinator.disableWaitlistMode(courseId)
            throw e
        }

        joinWaitlist(courseId, memberId)
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
