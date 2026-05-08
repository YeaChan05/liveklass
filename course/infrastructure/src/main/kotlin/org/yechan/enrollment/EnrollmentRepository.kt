package org.yechan.enrollment

interface EnrollmentRepository {
    fun save(enrollment: EnrollmentModel, courseId: Long): EnrollmentModel

    fun findById(enrollmentId: Long): EnrollmentModel?

    fun findByMemberId(memberId: Long): List<EnrollmentModel>
}
