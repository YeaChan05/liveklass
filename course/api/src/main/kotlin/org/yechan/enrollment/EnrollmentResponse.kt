package org.yechan.enrollment

data class EnrollmentResponse(
    val enrollmentId: Long?,
    val courseId: Long,
    val memberId: Long,
    val status: EnrollmentResponseStatus,
)

enum class EnrollmentResponseStatus {
    PENDING,
    CONFIRMED,
    CANCELLED,
    EXPIRED,
    WAITLISTED,
}
