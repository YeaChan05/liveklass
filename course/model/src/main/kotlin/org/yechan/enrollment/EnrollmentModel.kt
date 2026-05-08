package org.yechan.enrollment

import org.yechan.course.CourseInvalidStateException

interface EnrollmentIdentifier {
    val enrollmentId: Long?
}

interface EnrollmentProps {
    val courseId: Long
    val memberId: Long
    val status: EnrollmentStatus
}

data class EnrollmentModel(
    override val enrollmentId: Long? = null,
    override val courseId: Long,
    override val memberId: Long,
    override val status: EnrollmentStatus = EnrollmentStatus.PENDING,
) : EnrollmentProps,
    EnrollmentIdentifier {
    fun confirm(): EnrollmentModel {
        if (status != EnrollmentStatus.PENDING) {
            throw CourseInvalidStateException("결제 대기 상태의 신청만 확정할 수 있습니다.")
        }
        return copy(status = EnrollmentStatus.CONFIRMED)
    }

    fun confirmPayment(): EnrollmentModel = confirm()

    fun cancel(): EnrollmentModel {
        if (status == EnrollmentStatus.CANCELLED) {
            throw CourseInvalidStateException("이미 취소된 신청입니다.")
        }
        return copy(status = EnrollmentStatus.CANCELLED)
    }
}

enum class EnrollmentStatus {
    PENDING,
    CONFIRMED,
    CANCELLED,
}
