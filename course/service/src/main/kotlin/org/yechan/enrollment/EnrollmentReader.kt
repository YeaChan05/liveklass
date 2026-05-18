package org.yechan.enrollment

import org.springframework.transaction.annotation.Transactional
import org.yechan.course.CourseNotFoundException
import org.yechan.course.CourseRepository
import java.time.LocalDateTime

interface EnrollmentReader {
    fun findSeatOccupyingEnrollment(command: EnrollCourseCommand): EnrollmentInfo?

    fun getMyEnrollments(memberId: Long): List<EnrollmentInfo>

    fun requireOpenCourse(courseId: Long)

    fun findExpiredPaymentPendingTargets(
        now: LocalDateTime,
        limit: Int,
    ): List<EnrollmentExpirationTarget>
}

@Transactional(readOnly = true)
class EnrollmentRepositoryReader(
    private val courseRepository: CourseRepository,
    private val enrollmentRepository: EnrollmentRepository,
) : EnrollmentReader {
    override fun findSeatOccupyingEnrollment(command: EnrollCourseCommand): EnrollmentInfo? = enrollmentRepository.findByMemberIdAndCourseId(
        memberId = command.memberId,
        courseId = command.courseId,
    )?.takeIf(EnrollmentModel::isSeatOccupied)
        ?.toResult()

    override fun getMyEnrollments(memberId: Long): List<EnrollmentInfo> = enrollmentRepository
        .findHistoriesByMemberId(memberId)
        .map { it.toResult() }

    override fun requireOpenCourse(courseId: Long) {
        val course = courseRepository.findById(courseId) ?: throw CourseNotFoundException()
        course.validateIsOpen()
    }

    override fun findExpiredPaymentPendingTargets(
        now: LocalDateTime,
        limit: Int,
    ): List<EnrollmentExpirationTarget> = enrollmentRepository.findExpiredPaymentPendingTargets(now = now, limit = limit)
}
