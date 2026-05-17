package org.yechan.enrollment

import java.time.Instant
import java.time.LocalDateTime

interface EnrollmentWaitlistReader {
    fun isWaitlistMode(courseId: Long): Boolean

    fun findByMemberId(memberId: Long): List<EnrollmentWaitlistEntry>

    fun findCourseIds(): Set<Long>
}

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

    fun promoteAfterSeatRelease(
        courseId: Long,
        returnedSeatCount: Int = 1,
    )
}

class EnrollmentWaitlistRepositoryReader(
    private val waitlistRepository: EnrollmentWaitlistRepository,
) : EnrollmentWaitlistReader {
    override fun isWaitlistMode(courseId: Long): Boolean = waitlistRepository.isSoldOut(courseId)

    override fun findByMemberId(memberId: Long): List<EnrollmentWaitlistEntry> = waitlistRepository.findByMemberId(memberId)

    override fun findCourseIds(): Set<Long> = waitlistRepository.findCourseIds()
}

class EnrollmentWaitlistRepositoryWriter(
    private val waitlistRepository: EnrollmentWaitlistRepository,
    private val enrollmentWaitlistProcessor: EnrollmentWaitlistProcessor,
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

    override fun promoteAfterSeatRelease(
        courseId: Long,
        returnedSeatCount: Int,
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
