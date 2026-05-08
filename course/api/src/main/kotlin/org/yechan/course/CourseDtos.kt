package org.yechan.course

import jakarta.validation.constraints.Future
import jakarta.validation.constraints.Min
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Size
import java.math.BigDecimal
import java.time.LocalDateTime

data class CreateCourseRequest(
    @field:NotBlank(message = "강의 제목은 필수입니다.")
    @field:Size(max = 100, message = "강의 제목은 100자 이하로 입력해야 합니다.")
    val title: String = "",
    @field:NotBlank(message = "강의 설명은 필수입니다.")
    val description: String = "",
    @field:NotNull(message = "강의 가격은 필수입니다.")
    @field:Min(value = 0, message = "강의 가격은 0원 이상이어야 합니다.")
    val price: BigDecimal? = null,
    @field:Min(value = 1, message = "강의 정원은 1명 이상이어야 합니다.")
    val capacity: Int = 0,
    @field:NotNull(message = "수강 시작일은 필수입니다.")
    @field:Future(message = "수강 시작일은 미래여야 합니다.")
    val periodStart: LocalDateTime? = null,
    @field:NotNull(message = "수강 종료일은 필수입니다.")
    @field:Future(message = "수강 종료일은 미래여야 합니다.")
    val periodEnd: LocalDateTime? = null,
) {
    fun toCommand(creatorId: Long): CreateCourseCommand = CreateCourseCommand(
        creatorId = creatorId,
        title = title.trim(),
        description = description.trim(),
        price = Money(requireNotNull(price)),
        capacity = capacity,
        periodStart = requireNotNull(periodStart),
        periodEnd = requireNotNull(periodEnd),
    )
}

data class CourseResponse(
    val courseId: Long,
    val creatorId: Long,
    val title: String,
    val description: String,
    val price: BigDecimal,
    val capacity: Int,
    val seatLeftCount: Int,
    val periodStart: LocalDateTime,
    val periodEnd: LocalDateTime,
    val status: CourseStatus,
)
