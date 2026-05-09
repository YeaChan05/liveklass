package org.yechan.course

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Table
import org.yechan.BaseEntity
import org.yechan.enrollment.EnrollmentModel
import org.yechan.enrollment.EnrollmentModelData
import java.math.BigDecimal
import java.time.LocalDateTime

@Entity
@Table(name = "courses")
class CourseEntity private constructor(
    @field:Column(nullable = false)
    override var creatorId: Long,

    @field:Column(nullable = false, length = 100)
    override var title: String,

    @field:Column(nullable = false, columnDefinition = "TEXT")
    override var description: String,

    @field:Column(name = "price", nullable = false, precision = 19, scale = 2)
    var priceAmount: BigDecimal,

    @field:Column(nullable = false)
    override var capacity: Int,

    @field:Column(name = "seat_left_count", nullable = false)
    override var seatLeftCount: Int,

    @field:Column(name = "period_start", nullable = false)
    override var periodStart: LocalDateTime,

    @field:Column(name = "period_end", nullable = false)
    override var periodEnd: LocalDateTime,

    @field:Enumerated(EnumType.STRING)
    @field:Column(nullable = false, length = 20)
    override var status: CourseStatus,

) : BaseEntity(),
    CourseModel {
    override val courseId: Long?
        get() = id

    override val price: Money
        get() = Money(priceAmount)

    companion object {
        fun of(
            course: CourseModel,
            creatorId: Long,
        ): CourseEntity = CourseEntity(
            creatorId = creatorId,
            title = course.title,
            description = course.description,
            priceAmount = course.price.amount,
            capacity = course.capacity,
            seatLeftCount = course.seatLeftCount,
            periodStart = course.periodStart,
            periodEnd = course.periodEnd,
            status = course.status,
        ).apply {
            assignId(course.courseId)
        }
    }

    override fun open(): CourseModel {
        validateIsDraft()
        status = CourseStatus.OPEN
        return this
    }

    override fun close(): CourseModel {
        validateIsOpen()
        status = CourseStatus.CLOSED
        return this
    }

    override fun reserveSeat(): CourseModel {
        validateIsOpen()
        validateHasSeat()
        seatLeftCount -= 1
        return this
    }

    override fun releaseSeat(): CourseModel {
        if (seatLeftCount >= capacity) throw CourseInvalidStateException("남은 좌석 수는 정원을 초과할 수 없습니다.")
        seatLeftCount += 1
        return this
    }

    override fun requestEnrollment(
        memberId: Long,
        currentEnrollmentCount: Int?,
    ): EnrollmentModel {
        validateIsOpen()
        if (currentEnrollmentCount != null && currentEnrollmentCount >= capacity) {
            throw CourseInvalidStateException("강의 정원을 초과할 수 없습니다.")
        }
        return EnrollmentModelData(
            courseId = id ?: throw CourseInvalidStateException("저장된 강의만 신청할 수 있습니다."),
            memberId = memberId,
        )
    }

    private fun assignId(id: Long?) {
        this.id = id
    }

    private fun validateIsDraft() {
        if (status != CourseStatus.DRAFT) throw CourseInvalidStateException("초안 상태의 강의만 모집할 수 있습니다.")
    }

    private fun validateIsOpen() {
        if (status != CourseStatus.OPEN) throw CourseInvalidStateException("모집 중인 강의만 마감할 수 있습니다.")
    }

    private fun validateHasSeat() {
        if (seatLeftCount <= 0) throw CourseInvalidStateException("강의 정원을 초과할 수 없습니다.")
    }
}
