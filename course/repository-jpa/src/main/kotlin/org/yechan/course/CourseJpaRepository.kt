package org.yechan.course

import jakarta.persistence.LockModeType
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Lock
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param

interface CourseJpaRepository : JpaRepository<CourseEntity, Long> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from CourseEntity c where c.id = :courseId")
    fun findByIdForUpdate(@Param("courseId") courseId: Long): CourseEntity?
}
