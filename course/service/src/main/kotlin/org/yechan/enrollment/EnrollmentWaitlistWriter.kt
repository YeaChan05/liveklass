package org.yechan.enrollment

import java.time.Instant
import java.time.LocalDateTime

interface EnrollmentWaitlistWriter {
    fun enableWaitlistMode(courseId: Long)

    fun disableWaitlistMode(courseId: Long)

    fun joinWaitlist(
        courseId: Long,
        memberId: Long,
        requestedAt: Instant,
    )

    fun cancelWaitlist(
        courseId: Long,
        memberId: Long,
    )

    fun assignAfterSeatRelease(
        courseId: Long,
        returnedSeatCount: Int = 1,
    )
}

class EnrollmentWaitlistRepositoryWriter(
    private val waitlistRepository: EnrollmentWaitlistRepository,
    private val enrollmentWaitlistAssigner: EnrollmentWaitlistAssigner,
) : EnrollmentWaitlistWriter {
    override fun enableWaitlistMode(courseId: Long) {
        waitlistRepository.markSoldOut(courseId)
    }

    override fun disableWaitlistMode(courseId: Long) {
        waitlistRepository.clearSoldOut(courseId)
    }

    override fun joinWaitlist(
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

    override fun cancelWaitlist(
        courseId: Long,
        memberId: Long,
    ) {
        waitlistRepository.remove(courseId, memberId)
    }

    override fun assignAfterSeatRelease(
        courseId: Long,
        returnedSeatCount: Int,
    ) {
        try {
            repeat(returnedSeatCount.coerceAtLeast(0)) {
                assignOne(courseId)
            }
        } catch (e: RuntimeException) {
            updateSoldOutFlag(courseId)
            throw e
        }

        updateSoldOutFlag(courseId)
    }

    private fun assignOne(courseId: Long) {
        while (true) {
            val waitlist = waitlistRepository.peek(courseId) ?: return
            val result = enrollmentWaitlistAssigner.assign(
                EnrollmentWaitlistPromotionCandidate(
                    waitlist = waitlist,
                    assignedAt = LocalDateTime.now(),
                ),
            )

            waitlistRepository.remove(courseId, waitlist.memberId)

            if (result == EnrollmentWaitlistAssignResult.Promoted) {
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
