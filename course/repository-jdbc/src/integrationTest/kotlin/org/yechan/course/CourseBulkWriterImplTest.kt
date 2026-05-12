package org.yechan.course

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.jdbc.test.autoconfigure.JdbcTest
import org.springframework.context.annotation.Import
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.core.queryForObject
import org.springframework.test.context.ContextConfiguration
import org.yechan.TestConfig

@JdbcTest
@Import(CourseBulkWriterImpl::class)
@ContextConfiguration(classes = [TestConfig::class])
class CourseBulkWriterImplTest
@Autowired
constructor(
    private val jdbcTemplate: JdbcTemplate,
) {
    private val courseBulkWriter = CourseBulkWriterImpl(jdbcTemplate)

    @BeforeEach
    fun setUp() {
        jdbcTemplate.execute(
            """
                CREATE TABLE IF NOT EXISTS courses (
                    id BIGINT NOT NULL PRIMARY KEY,
                    creator_id BIGINT NOT NULL,
                    title VARCHAR(255) NOT NULL,
                    description VARCHAR(255) NOT NULL,
                    price DECIMAL(19, 2) NOT NULL,
                    capacity INT NOT NULL,
                    seat_left_count INT NOT NULL,
                    period_start DATETIME(6) NOT NULL,
                    period_end DATETIME(6) NOT NULL,
                    status VARCHAR(20) NOT NULL
                )
            """.trimIndent(),
        )
    }

    @Test
    fun `강의별 좌석을 일괄 예약할 수 있다`() {
        givenCourse(
            id = 1L,
            seatLeftCount = 10,
            status = CourseStatus.OPEN,
        )
        givenCourse(
            id = 2L,
            seatLeftCount = 5,
            status = CourseStatus.OPEN,
        )

        courseBulkWriter.reserveSeatsBulk(
            mapOf(
                1L to 3,
                2L to 2,
            ),
        )

        assertSeatLeftCount(1L, 7)
        assertSeatLeftCount(2L, 3)
    }

    @Test
    fun `차감 수량이 0 이하인 요청은 무시한다`() {
        givenCourse(
            id = 1L,
            seatLeftCount = 10,
            status = CourseStatus.OPEN,
        )

        courseBulkWriter.reserveSeatsBulk(
            mapOf(
                1L to 0,
                2L to -1,
            ),
        )

        assertSeatLeftCount(1L, 10)
    }

    @Test
    fun `남은 좌석보다 많이 예약하려 하면 실패한다`() {
        givenCourse(
            id = 1L,
            seatLeftCount = 1,
            status = CourseStatus.OPEN,
        )

        assertThatThrownBy {
            courseBulkWriter.reserveSeatsBulk(
                mapOf(1L to 2),
            )
        }.isInstanceOf(CourseInvalidStateException::class.java)
    }

    @Test
    fun `OPEN 상태가 아닌 강의는 예약할 수 없다`() {
        givenCourse(
            id = 1L,
            seatLeftCount = 10,
            status = CourseStatus.CLOSED,
        )

        assertThatThrownBy {
            courseBulkWriter.reserveSeatsBulk(
                mapOf(1L to 1),
            )
        }.isInstanceOf(CourseInvalidStateException::class.java)

        assertSeatLeftCount(1L, 10)
    }

    private fun givenCourse(
        id: Long,
        seatLeftCount: Int,
        status: CourseStatus,
        capacity: Int = 10,
    ) {
        jdbcTemplate.update(
            """
                INSERT INTO courses (
                    id,
                    creator_id,
                    title,
                    description,
                    price,
                    capacity,
                    seat_left_count,
                    period_start,
                    period_end,
                    status
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """.trimIndent(),
            id,
            1L,
            "Kotlin Basic",
            "Kotlin course",
            100_000,
            capacity,
            seatLeftCount,
            "2026-06-01 00:00:00",
            "2026-06-30 00:00:00",
            status.name,
        )
    }

    private fun assertSeatLeftCount(
        courseId: Long,
        expected: Int,
    ) {
        assertThat(findSeatLeftCount(courseId)).isEqualTo(expected)
    }

    private fun findSeatLeftCount(courseId: Long): Int = jdbcTemplate.queryForObject<Int>(
        "SELECT seat_left_count FROM courses WHERE id = ?",
        courseId,
    )!!
}
