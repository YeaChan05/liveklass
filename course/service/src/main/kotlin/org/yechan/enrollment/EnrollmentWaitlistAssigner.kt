package org.yechan.enrollment

import io.hypersistence.tsid.TSID
import org.springframework.transaction.annotation.Transactional
import java.time.Duration
import java.time.LocalDateTime

data class EnrollmentWaitlistAssignmentCandidate(
    val waitlist: EnrollmentWaitlistEntry,
    val assignedAt: LocalDateTime,
)

interface EnrollmentWaitlistAssigner {
    fun assign(candidate: EnrollmentWaitlistAssignmentCandidate): EnrollmentWaitlistAssignResult
}

open class EnrollmentWaitlistAssignmentService(
    private val courseBulkWriter: CourseBulkWriter,
    private val enrollmentBulkWriter: EnrollmentBulkWriter,
    private val enrollmentRepository: EnrollmentRepository,
    private val paymentPendingExpiresIn: Duration = Duration.ofMinutes(10),
) : EnrollmentWaitlistAssigner {
    @Transactional
    override fun assign(candidate: EnrollmentWaitlistAssignmentCandidate): EnrollmentWaitlistAssignResult {
        val waitlist = candidate.waitlist
        val enrollmentsByCourseAndMember = enrollmentRepository.findAllByCourseIdsAndMemberIds(
            courseIds = setOf(waitlist.courseId),
            memberIds = setOf(waitlist.memberId),
        ).associateBy { it.courseId to it.memberId }

        val assignable = waitlist.toAssignableEnrollment(
            courseId = waitlist.courseId,
            now = candidate.assignedAt,
            existingEnrollment = enrollmentsByCourseAndMember[waitlist.courseId to waitlist.memberId],
        ) ?: return EnrollmentWaitlistAssignResult.Invalid

        courseBulkWriter.reserveSeatsBulk(mapOf(assignable.courseId to 1))
        enrollmentBulkWriter.saveAllBulk(listOf(assignable))

        return EnrollmentWaitlistAssignResult.Assigned
    }

    private fun EnrollmentWaitlistEntry.toAssignableEnrollment(
        courseId: Long,
        now: LocalDateTime,
        existingEnrollment: EnrollmentModel?,
    ): EnrollmentModelData? = when (existingEnrollment?.status) {
        EnrollmentStatus.CONFIRMED,
        EnrollmentStatus.PENDING,
        -> null

        EnrollmentStatus.CANCELLED,
        EnrollmentStatus.EXPIRED,
        -> getEnrollment(
            enrollmentId = existingEnrollment.enrollmentId,
            courseId = courseId,
            now = now,
            paymentPendingExpiresIn = paymentPendingExpiresIn,
        )

        null -> getEnrollment(
            courseId = courseId,
            now = now,
            paymentPendingExpiresIn = paymentPendingExpiresIn,
        )
    }
}

enum class EnrollmentWaitlistAssignResult {
    Assigned,
    Invalid,
}

private fun EnrollmentWaitlistEntry.getEnrollment(
    enrollmentId: Long? = TSID.Factory.getTsid().toLong(),
    courseId: Long,
    now: LocalDateTime,
    paymentPendingExpiresIn: Duration,
): EnrollmentModelData = EnrollmentModelData(
    enrollmentId = enrollmentId,
    courseId = courseId,
    memberId = memberId,
    status = EnrollmentStatus.PENDING,
    paymentPendingStartedAt = now,
    paymentPendingExpiresAt = now.plus(paymentPendingExpiresIn),
)
