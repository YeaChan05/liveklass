package org.yechan.enrollment

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param

interface EnrollmentJpaRepository : JpaRepository<EnrollmentEntity, Long> {
    @Query("select e from EnrollmentEntity e where e.memberId = :memberId")
    fun findAllByMemberId(
        @Param("memberId") memberId: Long,
    ): List<EnrollmentEntity>
}
