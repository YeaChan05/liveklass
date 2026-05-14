package org.yechan.enrollment

import org.springframework.transaction.annotation.Transactional
import org.yechan.course.CourseInvalidStateException
import org.yechan.course.CourseNotFoundException
import org.yechan.course.CourseRepository
import org.yechan.course.EnrollmentNotFoundException

interface EnrollmentUseCase {
    fun enroll(command: EnrollCourseCommand): EnrollmentEnrollResult

    fun confirmEnrollment(command: EnrollmentStatusCommand): EnrollmentResult

    fun cancelEnrollment(command: EnrollmentStatusCommand): EnrollmentResult

    fun getMyEnrollments(memberId: Long): List<EnrollmentResult>
}

sealed interface EnrollmentEnrollTransactionResult {
    data class Enrolled(
        val enrollment: EnrollmentResult,
    ) : EnrollmentEnrollTransactionResult

    data object SoldOut : EnrollmentEnrollTransactionResult
}

data class EnrollmentCancelResult(
    val enrollment: EnrollmentResult,
    val courseId: Long,
)

@Transactional(readOnly = true)
class EnrollmentTransactionService(
    private val courseRepository: CourseRepository,
    private val enrollmentRepository: EnrollmentRepository,
) {
    @Transactional
    fun enroll(command: EnrollCourseCommand): EnrollmentEnrollTransactionResult {
        val memberId = command.memberId
        val courseId = command.courseId

        if (!courseRepository.reserveSeatIfAvailable(courseId)) {
            return EnrollmentEnrollTransactionResult.SoldOut
        }

        val enrollment = EnrollmentModelData(
            enrollmentId = null,
            courseId = courseId,
            memberId = memberId,
            status = EnrollmentStatus.PENDING,
        )

        return EnrollmentEnrollTransactionResult.Enrolled(
            enrollmentRepository.save(enrollment).toResult(),
        )
    }

    fun requireOpenCourse(courseId: Long) {
        val course = courseRepository.findById(courseId)
            ?: throw CourseNotFoundException()
        course.validateIsOpen()
    }

    @Transactional
    fun confirmEnrollment(command: EnrollmentStatusCommand): EnrollmentResult {
        val enrollment = ownedEnrollment(command.enrollmentId, command.memberId)
        val confirmed = enrollment.confirm()
        return enrollmentRepository.save(confirmed).toResult()
    }

    @Transactional
    fun cancelEnrollment(command: EnrollmentStatusCommand): EnrollmentCancelResult {
        val enrollment = ownedEnrollment(command.enrollmentId, command.memberId)
        val cancelled = enrollment.cancel()
        val savedEnrollment = enrollmentRepository.save(cancelled)

        courseRepository.releaseSeatIfPossible(enrollment.courseId)
            .also { if (!it) throw CourseInvalidStateException("좌석을 반환할 수 없습니다.") }

        return EnrollmentCancelResult(
            enrollment = savedEnrollment.toResult(),
            courseId = enrollment.courseId,
        )
    }

    private fun ownedEnrollment(
        enrollmentId: Long,
        memberId: Long,
    ) = enrollmentRepository.findById(enrollmentId)
        ?.takeIf { it.memberId == memberId }
        ?: throw EnrollmentNotFoundException()
}

internal fun EnrollmentModel.toResult(): EnrollmentResult = EnrollmentResult(
    enrollmentId = requireNotNull(enrollmentId),
    courseId = courseId,
    memberId = memberId,
    status = status,
)
