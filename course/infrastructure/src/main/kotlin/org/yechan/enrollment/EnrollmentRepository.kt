package org.yechan.enrollment

import java.time.Instant

interface EnrollmentRepository {
    fun save(enrollment: EnrollmentModel, courseId: Long): EnrollmentModel

    fun findById(enrollmentId: Long): EnrollmentModel?

    fun findByMemberId(memberId: Long): List<EnrollmentModel>
}

interface EnrollmentWaitlistRepository {
    fun enqueue(
        courseId: Long,
        memberId: Long,
        requestedAt: Instant,
    )

    fun pop(courseId: Long): EnrollmentWaitlistEntry?

    fun remove(
        courseId: Long,
        memberId: Long,
    )

    fun findCourseIds(): Set<Long>
}

data class EnrollmentWaitlistEntry(
    val courseId: Long,
    val memberId: Long,
    val requestedAt: Instant,
)
