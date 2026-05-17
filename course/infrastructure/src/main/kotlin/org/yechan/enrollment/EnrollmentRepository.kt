package org.yechan.enrollment

import java.time.Instant
import java.time.LocalDateTime

interface EnrollmentRepository {
    fun save(enrollment: EnrollmentModel): EnrollmentModel

    fun findById(enrollmentId: Long): EnrollmentModel?

    fun findByMemberId(memberId: Long): List<EnrollmentModel>

    fun findByMemberIdAndCourseId(
        memberId: Long,
        courseId: Long,
    ): EnrollmentModel?

    fun findAllByCourseIdsAndMemberIds(
        courseIds: Set<Long>,
        memberIds: Set<Long>,
    ): List<EnrollmentModel>

    fun findExpiredPaymentPendingTargets(
        now: LocalDateTime,
        limit: Int,
    ): List<EnrollmentExpirationTarget>

    fun expirePaymentPendingIfExpired(
        enrollmentId: Long,
        now: LocalDateTime,
    ): Boolean
}

interface EnrollmentWaitlistRepository {
    fun enqueue(
        courseId: Long,
        memberId: Long,
        requestedAt: Instant,
    )

    fun pop(courseId: Long): EnrollmentWaitlistEntry?

    fun peek(courseId: Long): EnrollmentWaitlistEntry?

    fun count(courseId: Long): Long

    fun rank(
        courseId: Long,
        memberId: Long,
    ): Long?

    fun findByMemberId(memberId: Long): List<EnrollmentWaitlistEntry>

    fun remove(
        courseId: Long,
        memberId: Long,
    )

    fun findCourseIds(): Set<Long>

    fun isSoldOut(courseId: Long): Boolean

    fun markSoldOut(courseId: Long)

    fun clearSoldOut(courseId: Long)
}

data class EnrollmentWaitlistEntry(
    val courseId: Long,
    val memberId: Long,
    val requestedAt: Instant,
)

data class EnrollmentExpirationTarget(
    val enrollmentId: Long,
    val courseId: Long,
)
