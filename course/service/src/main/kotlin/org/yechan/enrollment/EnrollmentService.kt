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
    private val enrollmentReader: EnrollmentReader,
    private val enrollmentWriter: EnrollmentWriter,
    private val waitlistReader: EnrollmentWaitlistReader,
    private val waitlistWriter: EnrollmentWaitlistWriter,
) : EnrollmentUseCase {
    override fun enroll(command: EnrollCourseCommand): EnrollResult {
        if (waitlistReader.isWaitlistMode(command.courseId)) {
            enrollmentReader.findSeatOccupyingEnrollment(command)?.let {
                return EnrollResult.Enrolled(it)
            }

            return joinWaitlist(command)
        }

        return when (val result = enrollmentWriter.enroll(command)) {
            is EnrollmentTransactionResult.Enrolled -> EnrollResult.Enrolled(result.enrollment)
            EnrollmentTransactionResult.SoldOut -> enableWaitlistModeAndJoin(command)
        }
    }

    override fun confirmEnrollment(command: EnrollmentStatusCommand): EnrollmentInfo = enrollmentWriter.confirmEnrollment(command)

    override fun cancelEnrollment(command: EnrollmentStatusCommand): EnrollmentInfo {
        val result = enrollmentWriter.cancelEnrollment(command)
        waitlistWriter.assignAfterSeatRelease(result.courseId)
        return result.enrollment
    }

    override fun cancelWaitlist(command: EnrollmentWaitlistCommand) {
        waitlistWriter.cancelWaitlist(command.courseId, command.memberId)
    }

    override fun getMyEnrollments(memberId: Long): List<EnrollmentInfo> = enrollmentReader.getMyEnrollments(memberId)

    override fun getMyWaitlist(memberId: Long): List<WaitlistInfo> = waitlistReader.findByMemberId(memberId)
        .map {
            WaitlistInfo(
                courseId = it.courseId,
                memberId = it.memberId,
                requestedAt = it.requestedAt,
            )
        }

    private fun joinWaitlist(command: EnrollCourseCommand): EnrollResult.Waitlisted {
        waitlistWriter.joinWaitlist(
            courseId = command.courseId,
            memberId = command.memberId,
            requestedAt = Instant.now(),
        )

        return EnrollResult.Waitlisted(
            courseId = command.courseId,
            memberId = command.memberId,
        )
    }

    private fun enableWaitlistModeAndJoin(command: EnrollCourseCommand): EnrollResult.Waitlisted {
        waitlistWriter.enableWaitlistMode(command.courseId)
        try {
            enrollmentReader.requireOpenCourse(command.courseId)
        } catch (e: RuntimeException) {
            waitlistWriter.disableWaitlistMode(command.courseId)
            throw e
        }

        return joinWaitlist(command)
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
