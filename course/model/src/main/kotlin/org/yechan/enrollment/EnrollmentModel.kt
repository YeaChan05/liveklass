package org.yechan.enrollment

import org.yechan.course.CourseInvalidStateException

interface EnrollmentIdentifier {
    val enrollmentId: Long?
}

interface EnrollmentProps {
    val courseId: Long
    val memberId: Long
    var status: EnrollmentStatus
}

interface EnrollmentModel :
    EnrollmentProps,
    EnrollmentIdentifier {
    fun confirm(): EnrollmentModel {
        if (status != EnrollmentStatus.PENDING) {
            throw CourseInvalidStateException("결제 대기 상태의 신청만 확정할 수 있습니다.")
        }
        status = EnrollmentStatus.CONFIRMED
        return this
    }

    fun confirmPayment(): EnrollmentModel = confirm()

    fun cancel(): EnrollmentModel {
        if (status != EnrollmentStatus.PENDING) {
            throw CourseInvalidStateException("결제 대기 상태에서만 취소가 가능합니다.")
        }
        status = EnrollmentStatus.CANCELLED
        return this
    }
}

data class EnrollmentModelData(
    override val enrollmentId: Long? = null,
    override val courseId: Long,
    override val memberId: Long,
    override var status: EnrollmentStatus = EnrollmentStatus.PENDING,
) : EnrollmentModel

enum class EnrollmentStatus {
    PENDING,
    CONFIRMED,
    CANCELLED,
}
