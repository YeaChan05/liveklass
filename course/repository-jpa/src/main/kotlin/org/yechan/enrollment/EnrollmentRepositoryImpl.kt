package org.yechan.enrollment

import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Sort
import java.time.LocalDateTime

class EnrollmentRepositoryImpl(
    private val enrollmentJpaRepository: EnrollmentJpaRepository,
) : EnrollmentRepository {
    override fun save(
        enrollment: EnrollmentModel,
        courseId: Long,
    ): EnrollmentModel = enrollmentJpaRepository.save(
        EnrollmentEntity.from(
            enrollment = enrollment,
            courseId = courseId,
        ),
    ).toDomain()

    override fun findById(enrollmentId: Long): EnrollmentModel? = enrollmentJpaRepository.findById(enrollmentId)
        .orElse(null)
        ?.toDomain()

    override fun findByMemberId(memberId: Long): List<EnrollmentModel> = enrollmentJpaRepository.findAllByMemberId(memberId)
        .map(EnrollmentEntity::toDomain)

    override fun findExpiredPaymentPendingTargets(
        now: LocalDateTime,
        limit: Int,
    ): List<EnrollmentExpirationTarget> = enrollmentJpaRepository.findExpiredPaymentPendingTargets(
        now = now,
        pageable = PageRequest.of(
            0,
            limit,
            Sort.by(Sort.Direction.ASC, "paymentPendingExpiresAt"),
        ),
    )

    override fun expirePaymentPendingIfExpired(
        enrollmentId: Long,
        now: LocalDateTime,
    ): Boolean = enrollmentJpaRepository.expirePaymentPendingIfExpired(
        enrollmentId = enrollmentId,
        now = now,
    ) == 1
}
