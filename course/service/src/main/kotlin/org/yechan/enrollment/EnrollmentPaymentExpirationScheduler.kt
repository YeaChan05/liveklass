package org.yechan.enrollment

import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.scheduling.annotation.Scheduled
import org.yechan.course.CourseInvalidStateException
import org.yechan.course.CourseRepository
import java.time.Clock
import java.time.LocalDateTime

private val log = KotlinLogging.logger {}

open class EnrollmentPaymentExpirationScheduler(
    private val enrollmentRepository: EnrollmentRepository,
    private val courseRepository: CourseRepository,
    private val waitlistRepository: EnrollmentWaitlistRepository,
    private val clock: Clock,
) {
    @Scheduled(fixedDelayString = $$"${enrollment.payment-expiration.fixed-delay-ms:60000}")
    fun expirePaymentPendingEnrollments() {
        val now = LocalDateTime.now(clock)
        val targets =
            enrollmentRepository.findExpiredPaymentPendingTargets(now = now, limit = 100)

        var expiredCount = 0

        targets.forEach { target ->
            enrollmentRepository.expirePaymentPendingIfExpired(
                enrollmentId = target.enrollmentId,
                now = now,
            ).also { if (!it) return@forEach }

            courseRepository.releaseSeatIfPossible(target.courseId)
                .also { if (!it) throw CourseInvalidStateException("만료된 수강 신청의 좌석을 반환할 수 없습니다.") }

            waitlistRepository.clearSoldOut(target.courseId)

            expiredCount++
        }

        if (expiredCount > 0) {
            log.info { "등록 대기 중인 $expiredCount 개의 수강이 만료되었습니다." }
        }
    }
}
