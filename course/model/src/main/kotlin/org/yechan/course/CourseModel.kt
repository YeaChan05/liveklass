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
        validateCapacity()
        if (!periodEnd.isAfter(periodStart)) throw IllegalArgumentException("수강 종료일은 시작일보다 빠를 수 없습니다.")
    }

    fun open(): CourseModel {
        validateIsDraft()
        return copy(status = CourseStatus.OPEN)
    }

    fun close(): CourseModel {
        validateIsOpen()
        return copy(status = CourseStatus.CLOSED)
    }

    fun requestEnrollment(
        memberId: Long,
        currentEnrollmentCount: Int,
    ): EnrollmentModel {
        validateIsOpen()
        validateCapacity(currentEnrollmentCount)
        return EnrollmentModel(
            courseId = courseId ?: error("저장된 강의만 신청할 수 있습니다."),
            memberId = memberId,
        )
    }

    private fun validateIsDraft() {
        if (status != CourseStatus.DRAFT) throw IllegalArgumentException("초안 상태의 강의만 모집할 수 있습니다.")
    }

    private fun validateIsOpen() {
        if (status != CourseStatus.OPEN) throw IllegalArgumentException("모집 중인 강의만 신청할 수 있습니다.")
    }

    private fun validateCapacity() {
        if (capacity <= 0) throw IllegalArgumentException("강의 정원은 1명 이상이어야 합니다.")
    }

    private fun validateCapacity(currentEnrollmentCount: Int) {
        if (currentEnrollmentCount >= capacity) throw IllegalArgumentException("강의 정원을 초과할 수 없습니다.")
    }
}

enum class CourseStatus {
    DRAFT,
    OPEN,
    CLOSED,
}
