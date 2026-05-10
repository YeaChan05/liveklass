package org.yechan.enrollment

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Index
import jakarta.persistence.Table
import jakarta.persistence.UniqueConstraint
import org.yechan.BaseEntity

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
            name = "idx_enrollments_course_status",
            columnList = "course_id, status",
        ),
    ],
)
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
