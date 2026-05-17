package org.yechan.enrollment

import io.hypersistence.tsid.TSID
import org.springframework.transaction.annotation.Transactional
import java.time.Duration
import java.time.LocalDateTime

data class EnrollmentWaitlistPromotionCandidate(
    val waitlist: EnrollmentWaitlistEntry,
    val promotedAt: LocalDateTime,
)

interface EnrollmentWaitlistProcessor {
    fun promote(candidate: EnrollmentWaitlistPromotionCandidate): EnrollmentWaitlistPromotionResult
}

open class EnrollmentWaitlistPromotionService(
    private val courseBulkWriter: CourseBulkWriter,
    private val enrollmentBulkWriter: EnrollmentBulkWriter,
    private val enrollmentRepository: EnrollmentRepository,
    private val paymentPendingExpiresIn: Duration = Duration.ofMinutes(10),
) : EnrollmentWaitlistProcessor {
    @Transactional
    override fun promote(candidate: EnrollmentWaitlistPromotionCandidate): EnrollmentWaitlistPromotionResult {
        val waitlist = candidate.waitlist
        val enrollmentsByCourseAndMember = enrollmentRepository.findAllByCourseIdsAndMemberIds(
            courseIds = setOf(waitlist.courseId),
            memberIds = setOf(waitlist.memberId),
        ).associateBy { it.courseId to it.memberId }

        val promotion = waitlist.toPromotableEnrollment(
            courseId = waitlist.courseId,
            now = candidate.promotedAt,
            existingEnrollment = enrollmentsByCourseAndMember[waitlist.courseId to waitlist.memberId],
        ) ?: return EnrollmentWaitlistPromotionResult.Invalid

        courseBulkWriter.reserveSeatsBulk(mapOf(promotion.courseId to 1))
        enrollmentBulkWriter.saveAllBulk(listOf(promotion))

        return EnrollmentWaitlistPromotionResult.Promoted
    }

    private fun EnrollmentWaitlistEntry.toPromotableEnrollment(
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

enum class EnrollmentWaitlistPromotionResult {
    Promoted,
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
