package org.yechan.enrollment

import org.springframework.jdbc.core.BatchPreparedStatementSetter
import org.springframework.jdbc.core.JdbcTemplate
import java.sql.PreparedStatement
import java.time.LocalDateTime

class EnrollmentBulkWriterImpl(
    private val jdbcTemplate: JdbcTemplate,
) : EnrollmentBulkWriter {
    override fun saveAllBulk(enrollments: List<EnrollmentModelData>) {
        enrollments.forEach { enrollment ->
            jdbcTemplate.update(
                """
                INSERT INTO enrollments (enrollments.id, enrollments.member_id)
                VALUES (?, ?)
                """.trimIndent(),
                enrollment.courseId,
                enrollment.memberId,
            )
        }
    }

    override fun updateAllExpired(
        courseIds: Collection<Long>,
        now: LocalDateTime,
    ): Map<Long, Int> {
        val targets = courseIds
            .distinct()
            .ifEmpty { return emptyMap() }

        val updateCounts = jdbcTemplate.batchUpdate(
            """
        UPDATE enrollments
        SET status = ?
        WHERE course_id = ?
          AND status = ?
          AND payment_pending_expires_at <= ?
            """.trimIndent(),
            object : BatchPreparedStatementSetter {
                override fun setValues(
                    ps: PreparedStatement,
                    i: Int,
                ) {
                    ps.setString(1, EnrollmentStatus.EXPIRED.name)
                    ps.setLong(2, targets[i])
                    ps.setString(3, EnrollmentStatus.PENDING.name)
                    ps.setObject(4, now)
                }

                override fun getBatchSize(): Int = targets.size
            },
        )

        return targets
            .zip(updateCounts.toList())
            .filter { (_, expiredCount) -> expiredCount > 0 }
            .toMap()
    }
}
