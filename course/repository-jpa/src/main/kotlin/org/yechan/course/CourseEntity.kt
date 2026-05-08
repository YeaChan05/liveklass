package org.yechan.course

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Table
import org.yechan.BaseEntity
import java.math.BigDecimal
import java.time.LocalDateTime

@Entity
@Table(name = "courses")
class CourseEntity private constructor(
    @field:Column(nullable = false)
    var creatorId: Long,
    @field:Column(nullable = false, length = 100)
    var title: String,
    @field:Column(nullable = false, columnDefinition = "TEXT")
    var description: String,
    @field:Column(nullable = false, precision = 19, scale = 2)
    var price: BigDecimal,
    @field:Column(nullable = false)
    var capacity: Int,
    @field:Column(name = "seat_left_count", nullable = false)
    var seatLeftCount: Int,
    @field:Column(name = "period_start", nullable = false)
    var periodStart: LocalDateTime,
    @field:Column(name = "period_end", nullable = false)
    var periodEnd: LocalDateTime,
    @field:Enumerated(EnumType.STRING)
    @field:Column(nullable = false, length = 20)
    var status: CourseStatus,
) : BaseEntity() {

    companion object {
        fun from(
            course: CourseModel,
            creatorId: Long,
        ): CourseEntity = CourseEntity(
            creatorId = creatorId,
            title = course.title,
            description = course.description,
            price = course.price.amount,
            capacity = course.capacity,
            seatLeftCount = course.seatLeftCount,
            periodStart = course.periodStart,
            periodEnd = course.periodEnd,
            status = course.status,
        ).apply {
            assignId(course.courseId)
        }
    }

    private fun assignId(id: Long?) {
        this.id = id
    }
}
