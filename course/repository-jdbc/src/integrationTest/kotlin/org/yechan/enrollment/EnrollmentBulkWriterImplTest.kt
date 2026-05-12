package org.yechan.enrollment

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.jdbc.test.autoconfigure.JdbcTest
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.context.ContextConfiguration
import org.yechan.TestConfig

@JdbcTest
@ContextConfiguration(classes = [TestConfig::class])
class EnrollmentBulkWriterImplTest @Autowired constructor(
    private val jdbcTemplate: JdbcTemplate,
) {
    private val enrollmentBulkWriter = EnrollmentBulkWriterImpl(jdbcTemplate)

    @BeforeEach
    fun setUp() {
        jdbcTemplate.execute(
            """
            CREATE TABLE IF NOT EXISTS enrollments (
                id BIGINT NOT NULL,
                member_id BIGINT NOT NULL
            )
            """.trimIndent(),
        )
    }

    @Test
    @DisplayName("수강 신청 목록을 저장할 수 있다")
    fun saveAllBulk() {
        val enrollments = listOf(
            EnrollmentModelData(
                courseId = 1L,
                memberId = 10L,
            ),
            EnrollmentModelData(
                courseId = 2L,
                memberId = 20L,
            ),
        )

        enrollmentBulkWriter.saveAllBulk(enrollments)

        val result = jdbcTemplate.query(
            """
            SELECT id, member_id
            FROM enrollments
            ORDER BY id
            """.trimIndent(),
        ) { rs, _ ->
            EnrollmentRow(
                id = rs.getLong("id"),
                memberId = rs.getLong("member_id"),
            )
        }

        assertThat(result).containsExactly(
            EnrollmentRow(
                id = 1L,
                memberId = 10L,
            ),
            EnrollmentRow(
                id = 2L,
                memberId = 20L,
            ),
        )
    }

    @Test
    @DisplayName("빈 목록이면 저장하지 않는다")
    fun saveAllBulkEmpty() {
        enrollmentBulkWriter.saveAllBulk(emptyList())

        val count = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM enrollments",
            Long::class.java,
        )

        assertThat(count).isZero()
    }

    private data class EnrollmentRow(
        val id: Long,
        val memberId: Long,
    )
}
