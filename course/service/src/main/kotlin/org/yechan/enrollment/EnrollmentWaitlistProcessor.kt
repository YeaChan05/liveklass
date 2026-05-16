package org.yechan.enrollment

import io.hypersistence.tsid.TSID
import org.springframework.transaction.annotation.Transactional
import java.time.Duration
import java.time.LocalDateTime

interface EnrollmentWaitlistProcessor {
    fun promote(candidates: List<EnrollmentWaitlistPromotionCandidate>)
}

open class EnrollmentWaitlistPromotionService(
    private val courseBulkWriter: CourseBulkWriter,
    private val enrollmentBulkWriter: EnrollmentBulkWriter,
    private val enrollmentRepository: EnrollmentRepository,
    private val paymentPendingExpiresIn: Duration = Duration.ofMinutes(10),
) : EnrollmentWaitlistProcessor {
    @Transactional
    override fun promote(candidates: List<EnrollmentWaitlistPromotionCandidate>) {
        val enrollmentsByCourseAndMember = enrollmentRepository.findAllByCourseIdsAndMemberIds(
            courseIds = candidates.map { it.waitlist.courseId }.toSet(),
            memberIds = candidates.map { it.waitlist.memberId }.toSet(),
        ).associateBy { it.courseId to it.memberId }

        val promotions = candidates.mapNotNull {
            val waitlist = it.waitlist
            waitlist.toPromotableEnrollment(
                courseId = waitlist.courseId,
                now = it.promotedAt,
                existingEnrollment = enrollmentsByCourseAndMember[waitlist.courseId to waitlist.memberId],
            )
        }
            .ifEmpty { return }

        val reservedCountsByCourseId = promotions
            .groupingBy { it.courseId }
            .eachCount()

        courseBulkWriter.reserveSeatsBulk(reservedCountsByCourseId)
        enrollmentBulkWriter.saveAllBulk(promotions)
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
