package org.yechan.enrollment

import org.springframework.jdbc.core.JdbcTemplate
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
            ON DUPLICATE KEY UPDATE
                course_id = VALUES(course_id),
                member_id = VALUES(member_id),
                status = VALUES(status),
                payment_pending_started_at = VALUES(payment_pending_started_at),
                payment_pending_expires_at = VALUES(payment_pending_expires_at)
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
        val targets = courseIds.distinct()
        if (targets.isEmpty()) return emptyMap()

        val expiredCounts = findExpiredCountsByCourseIds(
            courseIds = targets,
            now = now,
        )

        if (expiredCounts.isEmpty()) return emptyMap()

        updateExpiredByCourseIds(
            courseIds = expiredCounts.keys,
            now = now,
        )

        return expiredCounts
    }

    private fun findExpiredCountsByCourseIds(
        courseIds: Collection<Long>,
        now: LocalDateTime,
    ): Map<Long, Int> {
        val placeholders = courseIds.joinToString(",") { "?" }

        return jdbcTemplate.query(
            """
            SELECT course_id, COUNT(*) AS expired_count
            FROM enrollments
            WHERE course_id IN ($placeholders)
              AND status = ?
              AND payment_pending_expires_at <= ?
            GROUP BY course_id
            """.trimIndent(),
            { rs, _ ->
                rs.getLong("course_id") to rs.getInt("expired_count")
            },
            *courseIds.toTypedArray(),
            EnrollmentStatus.PENDING.name,
            now,
        ).toMap()
    }

    private fun updateExpiredByCourseIds(
        courseIds: Collection<Long>,
        now: LocalDateTime,
    ) {
        val placeholders = courseIds.joinToString(",") { "?" }

        jdbcTemplate.update(
            """
            UPDATE enrollments
            SET status = ?
            WHERE course_id IN ($placeholders)
              AND status = ?
              AND payment_pending_expires_at <= ?
            """.trimIndent(),
            *listOf(
                EnrollmentStatus.EXPIRED.name,
                *courseIds.toTypedArray(),
                EnrollmentStatus.PENDING.name,
                now,
            ).toTypedArray<Any>(),
        )
    }
}
