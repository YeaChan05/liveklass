package org.yechan.course

import java.time.LocalDate

interface CourseIdentifier {
    val courseId: Long?
}

interface CourseProps {
    val title: String
    val description: String
    val price: Long
    val capacity: Int
    val periodStart: LocalDate
    val periodEnd: LocalDate
    val status: CourseStatus
}

data class CourseModel(
    override val courseId: Long? = null,
    override val title: String,
    override val description: String,
    override val price: Long,
    override val capacity: Int,
    override val periodStart: LocalDate,
    override val periodEnd: LocalDate,
    override val status: CourseStatus = CourseStatus.DRAFT,
) : CourseProps,
    CourseIdentifier {
    init {
        require(capacity > 0) { "강의 정원은 1명 이상이어야 합니다." }
        require(!periodEnd.isBefore(periodStart)) { "수강 종료일은 시작일보다 빠를 수 없습니다." }
    }

    fun open(): CourseModel {
        require(status == CourseStatus.DRAFT) { "초안 상태의 강의만 모집할 수 있습니다." }
        return copy(status = CourseStatus.OPEN)
    }

    fun close(): CourseModel {
        require(status == CourseStatus.OPEN) { "모집 중인 강의만 마감할 수 있습니다." }
        return copy(status = CourseStatus.CLOSED)
    }

    fun requestEnrollment(
        memberId: Long,
        currentEnrollmentCount: Int,
    ): EnrollmentModel {
        require(status == CourseStatus.OPEN) { "모집 중인 강의만 신청할 수 있습니다." }
        require(currentEnrollmentCount < capacity) { "강의 정원을 초과할 수 없습니다." }
        return EnrollmentModel(
            courseId = courseId ?: error("저장된 강의만 신청할 수 있습니다."),
            memberId = memberId,
        )
    }
}

enum class CourseStatus {
    DRAFT,
    OPEN,
    CLOSED,
}
