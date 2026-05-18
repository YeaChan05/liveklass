package org.yechan.enrollment

internal fun EnrollmentModel.toResult(): EnrollmentInfo = EnrollmentInfo(
    enrollmentId = requireNotNull(enrollmentId),
    courseId = courseId,
    memberId = memberId,
    status = status,
)

sealed interface EnrollmentTransactionResult {
    data class Enrolled(
        val enrollment: EnrollmentInfo,
    ) : EnrollmentTransactionResult

    data object SoldOut : EnrollmentTransactionResult
}

data class EnrollmentCancelResult(
    val enrollment: EnrollmentInfo,
    val courseId: Long,
)
