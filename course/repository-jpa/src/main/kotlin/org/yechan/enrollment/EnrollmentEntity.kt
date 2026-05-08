package org.yechan.enrollment

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Table
import org.yechan.BaseEntity

@Entity
@Table(name = "enrollments")
class EnrollmentEntity private constructor(
    @field:Column(nullable = false)
    var courseId: Long,
    @field:Column(nullable = false)
    var memberId: Long,
    @field:Enumerated(EnumType.STRING)
    @field:Column(nullable = false, length = 20)
    var status: EnrollmentStatus,
) : BaseEntity() {
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

    fun toDomain(): EnrollmentModel = EnrollmentModel(
        enrollmentId = id,
        courseId = courseId,
        memberId = memberId,
        status = status,
    )

    private fun assignId(id: Long?) {
        this.id = id
    }
}
