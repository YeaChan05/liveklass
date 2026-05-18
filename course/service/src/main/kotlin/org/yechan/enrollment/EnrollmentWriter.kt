package org.yechan.enrollment

import org.springframework.transaction.annotation.Transactional
import org.yechan.course.CourseInvalidStateException
import org.yechan.course.CourseRepository
import org.yechan.course.EnrollmentNotFoundException
import java.time.Duration
import java.time.LocalDateTime

interface EnrollmentWriter {
    fun enroll(command: EnrollCourseCommand): EnrollmentTransactionResult

    fun confirmEnrollment(command: EnrollmentStatusCommand): EnrollmentInfo

    fun cancelEnrollment(command: EnrollmentStatusCommand): EnrollmentCancelResult
}

@Transactional(readOnly = true)
class EnrollmentRepositoryWriter(
    private val courseRepository: CourseRepository,
    private val enrollmentRepository: EnrollmentRepository,
    private val paymentPendingExpiresIn: Duration = Duration.ofMinutes(10),
) : EnrollmentWriter {
    @Transactional
    override fun enroll(command: EnrollCourseCommand): EnrollmentTransactionResult {
        val memberId = command.memberId
        val courseId = command.courseId
        val now = LocalDateTime.now()

        val existing =
            enrollmentRepository.findByMemberIdAndCourseId(
                memberId = memberId,
                courseId = courseId,
            )

        existing?.let { enrollment ->
            return when (enrollment.status) {
                EnrollmentStatus.PENDING,
                EnrollmentStatus.CONFIRMED,
                -> EnrollmentTransactionResult.Enrolled(
                    enrollment.toResult(),
                )

                EnrollmentStatus.CANCELLED,
                EnrollmentStatus.EXPIRED,
                -> reEnroll(
                    enrollment = enrollment,
                    courseId = courseId,
                    memberId = memberId,
                    now = now,
                )
            }
        }

        return createEnrollment(
            courseId = courseId,
            memberId = memberId,
            now = now,
        )
    }

    @Transactional
    override fun confirmEnrollment(command: EnrollmentStatusCommand): EnrollmentInfo {
        val enrollment = ownedEnrollment(command.enrollmentId, command.memberId)
        val confirmed = enrollment.confirm()
        return enrollmentRepository.save(confirmed).toResult()
    }

    @Transactional
    override fun cancelEnrollment(command: EnrollmentStatusCommand): EnrollmentCancelResult {
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

    private fun insert(
        enrollmentId: Long? = null,
        courseId: Long,
        memberId: Long,
        now: LocalDateTime,
    ): EnrollmentModel = enrollmentRepository.save(
        EnrollmentModelData(
            enrollmentId = enrollmentId,
            courseId = courseId,
            memberId = memberId,
            status = EnrollmentStatus.PENDING,
            paymentPendingStartedAt = now,
            paymentPendingExpiresAt = now.plus(paymentPendingExpiresIn),
        ),
    )

    private fun reEnroll(
        enrollment: EnrollmentModel,
        courseId: Long,
        memberId: Long,
        now: LocalDateTime,
    ): EnrollmentTransactionResult {
        if (!courseRepository.reserveSeatIfAvailable(courseId)) {
            return EnrollmentTransactionResult.SoldOut
        }

        val renewedEnrollment =
            insert(
                enrollmentId = enrollment.enrollmentId,
                courseId = courseId,
                memberId = memberId,
                now = now,
            )

        return EnrollmentTransactionResult.Enrolled(
            renewedEnrollment.toResult(),
        )
    }

    private fun createEnrollment(
        courseId: Long,
        memberId: Long,
        now: LocalDateTime,
    ): EnrollmentTransactionResult {
        if (!courseRepository.reserveSeatIfAvailable(courseId)) {
            return EnrollmentTransactionResult.SoldOut
        }

        val enrollment =
            insert(
                courseId = courseId,
                memberId = memberId,
                now = now,
            )

        return EnrollmentTransactionResult.Enrolled(
            enrollment.toResult(),
        )
    }

    private fun ownedEnrollment(
        enrollmentId: Long,
        memberId: Long,
    ) = enrollmentRepository.findById(enrollmentId)
        ?.takeIf { it.memberId == memberId }
        ?: throw EnrollmentNotFoundException()
}
