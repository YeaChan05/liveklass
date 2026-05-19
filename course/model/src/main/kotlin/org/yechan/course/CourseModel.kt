package org.yechan.course

import org.yechan.enrollment.EnrollmentModel // 모델간 의존 괜찮은가?
import org.yechan.enrollment.EnrollmentModelData
import java.time.LocalDateTime

interface CourseIdentifier {
    var courseId: Long?
}

interface CourseProps {
    val creatorId: Long?
    val title: String
    val description: String
    val capacity: Int
    val periodStart: LocalDateTime
    val periodEnd: LocalDateTime

    var price: Money
    var seatLeftCount: Int
    var status: CourseStatus
}

interface CourseModel :
    CourseProps,
    CourseIdentifier {
    fun open(): CourseModel {
        validateIsDraft()
        status = CourseStatus.OPEN
        return this
    }

    fun close(): CourseModel {
        validateIsOpen()
        status = CourseStatus.CLOSED
        return this
    }

    fun reserveSeat(): CourseModel {
        validateIsOpen()
        validateHasSeat()
        seatLeftCount -= 1
        return this
    }

    fun releaseSeat(): CourseModel {
        if (seatLeftCount >= capacity) throw CourseInvalidStateException("남은 좌석 수는 정원을 초과할 수 없습니다.")
        seatLeftCount += 1
        return this
    }

    fun requestEnrollment(
        memberId: Long,
        currentEnrollmentCount: Int? = null,
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

    fun validateCapacity() {
        if (capacity <= 0) throw CourseInvalidStateException("강의 정원은 1명 이상이어야 합니다.")
    }

    fun validateSeatLeftCount() {
        if (seatLeftCount < 0) throw CourseInvalidStateException("남은 좌석 수는 0보다 작을 수 없습니다.")
        if (seatLeftCount > capacity) throw CourseInvalidStateException("남은 좌석 수는 정원을 초과할 수 없습니다.")
    }

    fun validateIsOpen() {
        if (status != CourseStatus.OPEN) throw CourseInvalidStateException("모집중인 강의가 아닙니다.")
    }

    private fun validateIsDraft() {
        if (status != CourseStatus.DRAFT) throw CourseInvalidStateException("초안 상태의 강의만 모집할 수 있습니다.")
    }

    private fun validateHasSeat() {
        if (seatLeftCount <= 0) throw CourseInvalidStateException("강의 정원을 초과할 수 없습니다.")
    }
}

data class CourseModelData(
    override val creatorId: Long? = null,
    override val title: String,
    override val description: String,
    override val capacity: Int,
    override val periodStart: LocalDateTime,
    override val periodEnd: LocalDateTime,

    override var courseId: Long? = null,
    override var price: Money,
    override var seatLeftCount: Int = capacity,
    override var status: CourseStatus = CourseStatus.DRAFT,
) : CourseModel {
    init {
        if (creatorId == null) throw CourseInvalidStateException("강의 생성자는 필수입니다.")
        validateCapacity()
        validateSeatLeftCount()
        if (!periodEnd.isAfter(periodStart)) throw CourseInvalidStateException("수강 종료일은 시작일보다 빠를 수 없습니다.")
    }
}

enum class CourseStatus {
    DRAFT,
    OPEN,
    CLOSED,
}
