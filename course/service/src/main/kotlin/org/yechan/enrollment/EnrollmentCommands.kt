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
)
