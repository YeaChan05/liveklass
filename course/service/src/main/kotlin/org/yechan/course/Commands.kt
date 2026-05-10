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
    val currentEnrollmentCount: Int = capacity - seatLeftCount,
    val periodStart: LocalDateTime,
    val periodEnd: LocalDateTime,
    val status: CourseStatus,
    val enrolled: Boolean = false,
)
