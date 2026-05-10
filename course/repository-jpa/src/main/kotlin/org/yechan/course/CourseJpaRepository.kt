package org.yechan.course

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param

interface CourseJpaRepository : JpaRepository<CourseEntity, Long> {
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(
        """
        update CourseEntity c
        set c.seatLeftCount = c.seatLeftCount - 1
        where c.id = :courseId
          and c.status = :status
          and c.seatLeftCount > 0
        """,
    )
    fun reserveSeatIfAvailable(
        @Param("courseId") courseId: Long,
        @Param("status") status: CourseStatus = CourseStatus.OPEN,
    ): Int

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(
        """
        update CourseEntity c
        set c.seatLeftCount = c.seatLeftCount + 1
        where c.id = :courseId
          and c.seatLeftCount < c.capacity
        """,
    )
    fun releaseSeatIfPossible(
        @Param("courseId") courseId: Long,
    ): Int

    fun findAllByStatus(status: CourseStatus): List<CourseEntity>
}
