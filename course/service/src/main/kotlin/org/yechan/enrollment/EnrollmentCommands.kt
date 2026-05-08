package org.yechan.enrollment

data class EnrollCourseCommand(
    val memberId: Long,
    val courseId: Long,
)

data class EnrollmentStatusCommand(
    val memberId: Long,
    val enrollmentId: Long,
)

data class EnrollmentResult(
    val enrollmentId: Long,
    val courseId: Long,
    val memberId: Long,
    val status: EnrollmentStatus,
) {
    companion object {
        fun from(enrollment: EnrollmentModel): EnrollmentResult = EnrollmentResult(
            enrollmentId = requireNotNull(enrollment.enrollmentId),
            courseId = enrollment.courseId,
            memberId = enrollment.memberId,
            status = enrollment.status,
        )
    }
}
