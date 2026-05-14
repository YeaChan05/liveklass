package org.yechan.enrollment

import org.springframework.jdbc.core.BatchPreparedStatementSetter
import org.springframework.jdbc.core.JdbcTemplate
import java.sql.PreparedStatement
import java.time.LocalDateTime

class EnrollmentBulkWriterImpl(
    private val jdbcTemplate: JdbcTemplate,
) : EnrollmentBulkWriter {
    override fun saveAllBulk(enrollments: List<EnrollmentModelData>) {
        if (enrollments.isEmpty()) return

        jdbcTemplate.batchUpdate(
            """
        INSERT INTO enrollments (
            id,
            course_id,
            member_id,
            status,
            payment_pending_started_at,
            payment_pending_expires_at
        )
        VALUES (?, ?, ?, ?, ?, ?)
            """.trimIndent(),
            enrollments,
            enrollments.size,
        ) { ps, enrollment ->
            ps.setLong(1, enrollment.enrollmentId!!)
            ps.setLong(2, enrollment.courseId)
            ps.setLong(3, enrollment.memberId)
            ps.setString(4, enrollment.status.name)
            ps.setObject(5, enrollment.paymentPendingStartedAt)
            ps.setObject(6, enrollment.paymentPendingExpiresAt)
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
