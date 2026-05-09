package org.yechan.enrollment

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Table
import org.yechan.BaseEntity
import org.yechan.course.CourseInvalidStateException

@Entity
@Table(name = "enrollments")
class EnrollmentEntity private constructor(
    @field:Column(nullable = false)
    override var courseId: Long,

    @field:Column(nullable = false)
    override var memberId: Long,

    @field:Enumerated(EnumType.STRING)
    @field:Column(nullable = false, length = 20)
    override var status: EnrollmentStatus,

) : BaseEntity(),
    EnrollmentModel {
    override val enrollmentId: Long?
        get() = id

    companion object {
        fun from(
            enrollment: EnrollmentModel,
            courseId: Long,
        ): EnrollmentEntity = EnrollmentEntity(
            courseId = courseId,
            memberId = enrollment.memberId,
            status = enrollment.status,
        ).apply {
            assignId(enrollment.enrollmentId)
        }
    }

    override fun confirm(): EnrollmentModel {
        if (status != EnrollmentStatus.PENDING) {
            throw CourseInvalidStateException("결제 대기 상태의 신청만 확정할 수 있습니다.")
        }
        status = EnrollmentStatus.CONFIRMED
        return this
    }

    override fun confirmPayment(): EnrollmentModel = confirm()

    override fun cancel(): EnrollmentModel {
        if (status == EnrollmentStatus.CANCELLED) {
            throw CourseInvalidStateException("이미 취소된 신청입니다.")
        }
        status = EnrollmentStatus.CANCELLED
        return this
    }

    fun toDomain(): EnrollmentModel = EnrollmentModelData(
        enrollmentId = id,
        courseId = courseId,
        memberId = memberId,
        status = status,
    )

    private fun assignId(id: Long?) {
        this.id = id
    }
}
