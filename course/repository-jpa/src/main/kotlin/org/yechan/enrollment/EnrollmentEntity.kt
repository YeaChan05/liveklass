package org.yechan.enrollment

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Index
import jakarta.persistence.Table
import jakarta.persistence.UniqueConstraint
import org.yechan.BaseEntity
import java.time.LocalDateTime

@Entity
@Table(
    name = "enrollments",
    uniqueConstraints = [
        UniqueConstraint(
            name = "uk_enrollments_course_member",
            columnNames = ["course_id", "member_id"],
        ),
    ],
    indexes = [
        Index(
            name = "idx_enrollments_member_id",
            columnList = "member_id",
        ),
        Index(
            name = "idx_enrollments_member_status",
            columnList = "member_id, status",
        ),
        Index(
            name = "idx_enrollments_course_status",
            columnList = "course_id, status",
        ),
        Index(
            name = "idx_enrollments_status_payment_pending_expires_at",
            columnList = "status, payment_pending_expires_at",
        ),
    ],
)
class EnrollmentEntity private constructor(
    @field:Column(name = "course_id", nullable = false)
    override var courseId: Long,

    @field:Column(name = "member_id", nullable = false)
    override var memberId: Long,

    @field:Enumerated(EnumType.STRING)
    @field:Column(nullable = false, length = 20)
    override var status: EnrollmentStatus,

    @field:Column(name = "payment_pending_started_at", nullable = false)
    override var paymentPendingStartedAt: LocalDateTime,

    @field:Column(name = "payment_pending_expires_at", nullable = false)
    override var paymentPendingExpiresAt: LocalDateTime,
) : BaseEntity(),
    EnrollmentModel {
    override var enrollmentId: Long?
        get() = id
        set(value) {
            id = value
        }

    companion object {
        fun from(
            enrollment: EnrollmentModel,
        ): EnrollmentEntity = EnrollmentEntity(
            courseId = enrollment.courseId,
            memberId = enrollment.memberId,
            status = enrollment.status,
            paymentPendingStartedAt = enrollment.paymentPendingStartedAt,
            paymentPendingExpiresAt = enrollment.paymentPendingExpiresAt,
        ).apply {
            assignId(enrollment.enrollmentId)
        }
    }

    fun toDomain(): EnrollmentModel = EnrollmentModelData(
        enrollmentId = id,
        courseId = courseId,
        memberId = memberId,
        status = status,
        paymentPendingStartedAt = paymentPendingStartedAt,
        paymentPendingExpiresAt = paymentPendingExpiresAt,
    )

    private fun assignId(id: Long?) {
        this.id = id
    }
}
