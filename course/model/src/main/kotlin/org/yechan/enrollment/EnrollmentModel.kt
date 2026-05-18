package org.yechan.enrollment

import org.yechan.course.CourseInvalidStateException
import java.time.LocalDateTime

interface EnrollmentIdentifier {
    var enrollmentId: Long?
}

interface EnrollmentProps {
    val courseId: Long
    val memberId: Long
    var status: EnrollmentStatus
    val paymentPendingStartedAt: LocalDateTime
    val paymentPendingExpiresAt: LocalDateTime
}

interface EnrollmentModel :
    EnrollmentProps,
    EnrollmentIdentifier {
    fun confirm(
        now: LocalDateTime = LocalDateTime.now(),
    ): EnrollmentModel {
        if (status != EnrollmentStatus.PENDING) {
            throw CourseInvalidStateException("결제 대기 상태의 신청만 확정할 수 있습니다.")
        }

        if (isPaymentPendingExpired(now)) {
            throw CourseInvalidStateException("결제 대기 시간이 만료되었습니다.")
        }

        status = EnrollmentStatus.CONFIRMED
        return this
    }

    fun confirmPayment(
        now: LocalDateTime = LocalDateTime.now(),
    ): EnrollmentModel = confirm(now)

    fun cancel(): EnrollmentModel {
        if (status != EnrollmentStatus.PENDING) {
            throw CourseInvalidStateException("결제 대기 상태에서만 취소가 가능합니다.")
        }

        status = EnrollmentStatus.CANCELLED
        return this
    }

    fun expirePaymentPending(
        now: LocalDateTime = LocalDateTime.now(),
    ): EnrollmentModel {
        if (status != EnrollmentStatus.PENDING) {
            throw CourseInvalidStateException("결제 대기 상태의 신청만 만료 처리할 수 있습니다.")
        }

        if (!isPaymentPendingExpired(now)) {
            throw CourseInvalidStateException("결제 대기 시간이 아직 만료되지 않았습니다.")
        }

        status = EnrollmentStatus.EXPIRED
        return this
    }

    fun isPaymentPendingExpired(
        now: LocalDateTime = LocalDateTime.now(),
    ): Boolean = status == EnrollmentStatus.PENDING &&
        !now.isBefore(paymentPendingExpiresAt)

    fun isSeatOccupied(): Boolean = status == EnrollmentStatus.PENDING ||
        status == EnrollmentStatus.CONFIRMED

    fun isVisibleInMyEnrollmentHistory(): Boolean = status.isVisibleInMyEnrollmentHistory()
}

data class EnrollmentModelData(
    override var enrollmentId: Long? = null,
    override val courseId: Long,
    override val memberId: Long,
    override var status: EnrollmentStatus = EnrollmentStatus.PENDING,
    override val paymentPendingStartedAt: LocalDateTime = LocalDateTime.now(),
    override val paymentPendingExpiresAt: LocalDateTime = paymentPendingStartedAt.plusMinutes(10),
) : EnrollmentModel {
    init {
        require(paymentPendingExpiresAt.isAfter(paymentPendingStartedAt)) {
            "결제 대기 만료 시각은 결제 대기 시작 시각보다 이후여야 합니다."
        }
    }
}

enum class EnrollmentStatus {
    PENDING,
    CONFIRMED,
    CANCELLED,
    EXPIRED,
    ;

    fun isVisibleInMyEnrollmentHistory(): Boolean = this in myEnrollmentHistoryStatuses()

    companion object {
        fun myEnrollmentHistoryStatuses(): Set<EnrollmentStatus> = setOf(
            CONFIRMED,
            CANCELLED,
        )
    }
}
