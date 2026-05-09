package org.yechan.course

import org.yechan.enrollment.EnrollmentModel
import org.yechan.enrollment.EnrollmentModelData
import java.time.LocalDateTime

interface CourseIdentifier {
    val courseId: Long?
}

interface CourseProps {
    val creatorId: Long?
    val title: String
    val description: String
    val price: Money
    val capacity: Int
    val seatLeftCount: Int
    val periodStart: LocalDateTime
    val periodEnd: LocalDateTime
    val status: CourseStatus
}

interface CourseModel :
    CourseProps,
    CourseIdentifier {
    fun open(): CourseModel

    fun close(): CourseModel

    fun reserveSeat(): CourseModel

    fun releaseSeat(): CourseModel

    fun requestEnrollment(memberId: Long): EnrollmentModel = requestEnrollment(
        memberId = memberId,
        currentEnrollmentCount = null,
    )

    fun requestEnrollment(
        memberId: Long,
        currentEnrollmentCount: Int?,
    ): EnrollmentModel
}

data class CourseModelData(
    override val courseId: Long? = null,
    override val creatorId: Long? = null,
    override val title: String,
    override val description: String,
    override val price: Money,
    override val capacity: Int,
    override val seatLeftCount: Int = capacity,
    override val periodStart: LocalDateTime,
    override val periodEnd: LocalDateTime,
    override val status: CourseStatus = CourseStatus.DRAFT,
) : CourseModel {
    init {
        if (creatorId == null) throw CourseInvalidStateException("강의 생성자는 필수입니다.")
        validateCapacity()
        validateSeatLeftCount()
        if (!periodEnd.isAfter(periodStart)) throw CourseInvalidStateException("수강 종료일은 시작일보다 빠를 수 없습니다.")
    }

    override fun open(): CourseModel {
        validateIsDraft()
        return copy(status = CourseStatus.OPEN)
    }

    override fun close(): CourseModel {
        validateIsOpen()
        return copy(status = CourseStatus.CLOSED)
    }

    override fun reserveSeat(): CourseModel {
        validateIsOpen()
        validateHasSeat()
        return copy(seatLeftCount = seatLeftCount - 1)
    }

    override fun releaseSeat(): CourseModel {
        if (seatLeftCount >= capacity) throw CourseInvalidStateException("남은 좌석 수는 정원을 초과할 수 없습니다.")
        return copy(seatLeftCount = seatLeftCount + 1)
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
            courseId = courseId ?: throw CourseInvalidStateException("저장된 강의만 신청할 수 있습니다."),
            memberId = memberId,
        )
    }

    private fun validateIsDraft() {
        if (status != CourseStatus.DRAFT) throw CourseInvalidStateException("초안 상태의 강의만 모집할 수 있습니다.")
    }

    private fun validateIsOpen() {
        if (status != CourseStatus.OPEN) throw CourseInvalidStateException("모집 중인 강의만 마감할 수 있습니다.")
    }

    private fun validateCapacity() {
        if (capacity <= 0) throw CourseInvalidStateException("강의 정원은 1명 이상이어야 합니다.")
    }

    private fun validateSeatLeftCount() {
        if (seatLeftCount < 0) throw CourseInvalidStateException("남은 좌석 수는 0보다 작을 수 없습니다.")
        if (seatLeftCount > capacity) throw CourseInvalidStateException("남은 좌석 수는 정원을 초과할 수 없습니다.")
    }

    private fun validateHasSeat() {
        if (seatLeftCount <= 0) throw CourseInvalidStateException("강의 정원을 초과할 수 없습니다.")
    }
}

enum class CourseStatus {
    DRAFT,
    OPEN,
    CLOSED,
}
