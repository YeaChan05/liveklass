package org.yechan.enrollment

import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.time.LocalDateTime

interface EnrollmentJpaRepository : JpaRepository<EnrollmentEntity, Long> {
    fun findAllByMemberId(memberId: Long): List<EnrollmentEntity>

    fun findAllByMemberIdAndStatusIn(
        memberId: Long,
        statuses: Collection<EnrollmentStatus>,
    ): List<EnrollmentEntity>

    fun findByMemberIdAndCourseId(
        memberId: Long,
        courseId: Long,
    ): EnrollmentEntity?

    fun findAllByCourseIdInAndMemberIdIn(
        courseIds: Collection<Long>,
        memberIds: Collection<Long>,
    ): List<EnrollmentEntity>

    @Query(
        """
        select new org.yechan.enrollment.EnrollmentExpirationTarget(e.id, e.courseId)
        from EnrollmentEntity e
        where e.status = 'PENDING'
          and e.paymentPendingExpiresAt <= :now
        order by e.paymentPendingExpiresAt asc
        """,
    )
    fun findExpiredPaymentPendingTargets(
        @Param("now") now: LocalDateTime,
        pageable: Pageable,
    ): List<EnrollmentExpirationTarget>

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(
        """
        update EnrollmentEntity e
        set e.status = 'EXPIRED'
        where e.id = :enrollmentId
          and e.status = 'PENDING'
          and e.paymentPendingExpiresAt <= :now
        """,
    )
    fun expirePaymentPendingIfExpired(
        @Param("enrollmentId") enrollmentId: Long,
        @Param("now") now: LocalDateTime,
    ): Int
}
