package org.yechan.enrollment

import java.time.Instant
import java.time.LocalDateTime

class EnrollmentWaitlistCoordinator(
    private val waitlistRepository: EnrollmentWaitlistRepository,
    private val enrollmentWaitlistProcessor: EnrollmentWaitlistProcessor,
) {
    fun isWaitlistMode(courseId: Long): Boolean = waitlistRepository.isSoldOut(courseId)

    fun enableWaitlistMode(courseId: Long) {
        waitlistRepository.markSoldOut(courseId)
    }

    fun disableWaitlistMode(courseId: Long) {
        waitlistRepository.clearSoldOut(courseId)
    }

    fun joinWaitlist(
        courseId: Long,
        memberId: Long,
        requestedAt: Instant,
    ) {
        waitlistRepository.enqueue(
            courseId = courseId,
            memberId = memberId,
            requestedAt = requestedAt,
        )
    }

    fun cancelWaitlist(
        courseId: Long,
        memberId: Long,
    ) {
        waitlistRepository.remove(courseId, memberId)
    }

    fun findByMemberId(memberId: Long): List<EnrollmentWaitlistEntry> = waitlistRepository.findByMemberId(memberId)

    fun promoteAfterSeatRelease(
        courseId: Long,
        returnedSeatCount: Int = 1,
    ) {
        try {
            repeat(returnedSeatCount.coerceAtLeast(0)) {
                promoteOne(courseId)
            }
        } catch (e: RuntimeException) {
            updateSoldOutFlag(courseId)
            throw e
        }

        updateSoldOutFlag(courseId)
    }

    private fun promoteOne(courseId: Long) {
        while (true) {
            val waitlist = waitlistRepository.peek(courseId) ?: return
            val result = enrollmentWaitlistProcessor.promote(
                EnrollmentWaitlistPromotionCandidate(
                    waitlist = waitlist,
                    promotedAt = LocalDateTime.now(),
                ),
            )

            waitlistRepository.remove(courseId, waitlist.memberId)

            if (result == EnrollmentWaitlistPromotionResult.Promoted) {
                return
            }
        }
    }

    private fun updateSoldOutFlag(courseId: Long) {
        if (waitlistRepository.count(courseId) > 0) {
            waitlistRepository.markSoldOut(courseId)
        } else {
            waitlistRepository.clearSoldOut(courseId)
        }
    }
}
