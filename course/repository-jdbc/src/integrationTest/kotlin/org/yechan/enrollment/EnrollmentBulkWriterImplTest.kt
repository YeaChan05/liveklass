package org.yechan.enrollment

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.jdbc.test.autoconfigure.JdbcTest
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.core.queryForObject
import org.springframework.test.context.ContextConfiguration
import org.yechan.TestConfig
import java.time.LocalDateTime

@JdbcTest
@ContextConfiguration(classes = [TestConfig::class])
class EnrollmentBulkWriterImplTest @Autowired constructor(
    private val jdbcTemplate: JdbcTemplate,
) {
    private val enrollmentBulkWriter = EnrollmentBulkWriterImpl(jdbcTemplate)

    @Test
    fun `bulk 삽입한 만큼 저장된다`() {
        val enrollments = listOf(
            EnrollmentModelData(
                enrollmentId = 1L,
                courseId = 1L,
                memberId = 10L,
            ),
            EnrollmentModelData(
                enrollmentId = 2L,
                courseId = 2L,
                memberId = 20L,
            ),
        )

        enrollmentBulkWriter.saveAllBulk(enrollments)

        val count = jdbcTemplate.queryForObject<Long>(
            "SELECT COUNT(*) FROM enrollments",
        )

        assertThat(count).isEqualTo(2)
    }

    @Test
    fun `bulk 삽입 대상이 비어 있으면 저장하지 않는다`() {
        enrollmentBulkWriter.saveAllBulk(emptyList())

        val count = jdbcTemplate.queryForObject<Long>(
            "SELECT COUNT(*) FROM enrollments",
        )

        assertThat(count).isZero()
    }

    @Test
    fun `기존 enrollment id가 존재하면 upsert된다`() {
        enrollmentBulkWriter.saveAllBulk(
            listOf(
                EnrollmentModelData(
                    enrollmentId = 1L,
                    courseId = 1L,
                    memberId = 10L,
                    status = EnrollmentStatus.PENDING,
                ),
            ),
        )

        enrollmentBulkWriter.saveAllBulk(
            listOf(
                EnrollmentModelData(
                    enrollmentId = 1L,
                    courseId = 2L,
                    memberId = 99L,
                    status = EnrollmentStatus.CONFIRMED,
                ),
            ),
        )

        val result = jdbcTemplate.queryForMap(
            """
            SELECT course_id, member_id, status
            FROM enrollments
            WHERE id = 1
            """.trimIndent(),
        )

        assertThat(result["course_id"]).isEqualTo(2L)
        assertThat(result["member_id"]).isEqualTo(99L)
        assertThat(result["status"]).isEqualTo("CONFIRMED")
    }

    @Test
    fun `만료 시간이 지난 결제 대기 수강 신청은 만료 처리된다`() {
        val now = LocalDateTime.of(2026, 5, 16, 12, 0)

        enrollmentBulkWriter.saveAllBulk(
            listOf(
                EnrollmentModelData(
                    enrollmentId = 1L,
                    courseId = 1L,
                    memberId = 10L,
                    status = EnrollmentStatus.PENDING,
                    paymentPendingStartedAt = now.minusMinutes(20),
                    paymentPendingExpiresAt = now.minusMinutes(1),
                ),
                EnrollmentModelData(
                    enrollmentId = 2L,
                    courseId = 1L,
                    memberId = 20L,
                    status = EnrollmentStatus.PENDING,
                    paymentPendingStartedAt = now.minusMinutes(20),
                    paymentPendingExpiresAt = now.minusMinutes(1),
                ),
                EnrollmentModelData(
                    enrollmentId = 3L,
                    courseId = 2L,
                    memberId = 30L,
                    status = EnrollmentStatus.PENDING,
                    paymentPendingStartedAt = now.minusMinutes(5),
                    paymentPendingExpiresAt = now.plusMinutes(5),
                ),
            ),
        )

        val result = enrollmentBulkWriter.updateAllExpired(
            courseIds = listOf(1L, 2L),
            now = now,
        )

        assertThat(result).containsExactlyEntriesOf(
            mapOf(1L to 2),
        )

        val statuses = findStatuses()

        assertThat(statuses).containsExactlyEntriesOf(
            mapOf(
                1L to EnrollmentStatus.EXPIRED,
                2L to EnrollmentStatus.EXPIRED,
                3L to EnrollmentStatus.PENDING,
            ),
        )
    }

    @Test
    fun `확정된 수강 신청은 만료 처리하지 않는다`() {
        val now = LocalDateTime.of(2026, 5, 16, 12, 0)

        enrollmentBulkWriter.saveAllBulk(
            listOf(
                EnrollmentModelData(
                    enrollmentId = 1L,
                    courseId = 1L,
                    memberId = 10L,
                    status = EnrollmentStatus.CONFIRMED,
                    paymentPendingStartedAt = now.minusMinutes(20),
                    paymentPendingExpiresAt = now.minusMinutes(1),
                ),
            ),
        )

        val result = enrollmentBulkWriter.updateAllExpired(
            courseIds = listOf(1L),
            now = now,
        )

        assertThat(result).isEmpty()

        val statuses = findStatuses()

        assertThat(statuses).containsExactlyEntriesOf(
            mapOf(
                1L to EnrollmentStatus.CONFIRMED,
            ),
        )
    }

    @Test
    fun `만료 대상 강의가 비어 있으면 만료 처리하지 않는다`() {
        val now = LocalDateTime.of(2026, 5, 16, 12, 0)

        val result = enrollmentBulkWriter.updateAllExpired(
            courseIds = emptyList(),
            now = now,
        )

        assertThat(result).isEmpty()
    }

    private fun findStatuses(): Map<Long, EnrollmentStatus> = jdbcTemplate.query(
        """
            SELECT id, status
            FROM enrollments
            ORDER BY id
        """.trimIndent(),
    ) { rs, _ ->
        rs.getLong("id") to EnrollmentStatus.valueOf(
            rs.getString("status"),
        )
    }.toMap()
}
