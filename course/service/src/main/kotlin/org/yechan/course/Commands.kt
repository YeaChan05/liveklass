package org.yechan.course

import java.time.LocalDateTime

data class CreateCourseCommand(
    val title: String,
    val description: String,
    val price: Money,
    val capacity: Int,
    val periodStart: LocalDateTime,
    val periodEnd: LocalDateTime,
)

data class CourseStatusCommand(
    val memberId: Long,
    val courseId: Long,
)

data class CourseResult(
    val courseId: Long,
    val creatorId: Long,
    val title: String,
    val description: String,
    val price: Money,
    val capacity: Int,
    val seatLeftCount: Int,
    val periodStart: LocalDateTime,
    val periodEnd: LocalDateTime,
    val status: CourseStatus,
) {
    companion object {
        fun from(course: CourseModel): CourseResult = CourseResult(
            courseId = requireNotNull(course.courseId),
            creatorId = requireNotNull(course.creatorId),
            title = course.title,
            description = course.description,
            price = course.price,
            capacity = course.capacity,
            seatLeftCount = course.seatLeftCount,
            periodStart = course.periodStart,
            periodEnd = course.periodEnd,
            status = course.status,
        )
    }
}
