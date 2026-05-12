package org.yechan.enrollment

import org.springframework.jdbc.core.JdbcTemplate

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
}
