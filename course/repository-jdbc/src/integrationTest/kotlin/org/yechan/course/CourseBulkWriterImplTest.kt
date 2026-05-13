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
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional
import org.yechan.TestConfig
import org.yechan.enrollment.CourseBulkWriter

@JdbcTest
@Import(CourseBulkWriterImpl::class)
@ContextConfiguration(classes = [TestConfig::class])
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class CourseBulkWriterImplTest @Autowired constructor(
    private val jdbcTemplate: JdbcTemplate,
    private val courseBulkWriter: CourseBulkWriter,
) {
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

        jdbcTemplate.update("DELETE FROM courses")
    }

    @Test
    fun `강의별 좌석을 서로 다른 수량으로 일괄 예약할 수 있다`() {
        givenCourse(id = 1L, seatLeftCount = 10, capacity = 10)
        givenCourse(id = 2L, seatLeftCount = 5, capacity = 10)
        givenCourse(id = 3L, seatLeftCount = 7, capacity = 10)

        courseBulkWriter.reserveSeatsBulk(
            mapOf(
                1L to 3,
                2L to 1,
                3L to 5,
            ),
        )

        assertSeatLeftCount(1L, 7)
        assertSeatLeftCount(2L, 4)
        assertSeatLeftCount(3L, 2)
    }

    @Test
    fun `남은 좌석과 같은 수량까지는 예약할 수 있다`() {
        givenCourse(id = 1L, seatLeftCount = 3, capacity = 10)

        courseBulkWriter.reserveSeatsBulk(
            mapOf(1L to 3),
        )

        assertSeatLeftCount(1L, 0)
    }

    @Test
    fun `차감 수량이 0 이하인 예약 요청은 무시한다`() {
        givenCourse(id = 1L, seatLeftCount = 10, capacity = 10)

        courseBulkWriter.reserveSeatsBulk(
            mapOf(
                1L to 0,
                2L to -1,
            ),
        )

        assertSeatLeftCount(1L, 10)
    }

    @Test
    fun `예약 요청 중 0 이하 수량은 무시하고 양수 수량만 처리한다`() {
        givenCourse(id = 1L, seatLeftCount = 10, capacity = 10)
        givenCourse(id = 2L, seatLeftCount = 10, capacity = 10)

        courseBulkWriter.reserveSeatsBulk(
            mapOf(
                1L to 3,
                2L to 0,
                3L to -1,
            ),
        )

        assertSeatLeftCount(1L, 7)
        assertSeatLeftCount(2L, 10)
    }

    @Test
    fun `빈 예약 요청은 아무 작업도 하지 않는다`() {
        givenCourse(id = 1L, seatLeftCount = 10, capacity = 10)

        courseBulkWriter.reserveSeatsBulk(emptyMap())

        assertSeatLeftCount(1L, 10)
    }

    @Test
    fun `남은 좌석보다 많이 예약하려 하면 실패한다`() {
        givenCourse(id = 1L, seatLeftCount = 1, capacity = 10)

        assertThatThrownBy {
            courseBulkWriter.reserveSeatsBulk(
                mapOf(1L to 2),
            )
        }.isInstanceOf(CourseInvalidStateException::class.java)

        assertSeatLeftCount(1L, 1)
    }

    @Test
    fun `여러 예약 요청 중 하나라도 남은 좌석이 부족하면 전체 예약을 롤백한다`() {
        givenCourse(id = 1L, seatLeftCount = 10, capacity = 10)
        givenCourse(id = 2L, seatLeftCount = 1, capacity = 10)

        assertThatThrownBy {
            courseBulkWriter.reserveSeatsBulk(
                mapOf(
                    1L to 3,
                    2L to 2,
                ),
            )
        }.isInstanceOf(CourseInvalidStateException::class.java)

        assertSeatLeftCount(1L, 10)
        assertSeatLeftCount(2L, 1)
    }

    @Test
    fun `OPEN 상태가 아닌 강의는 예약할 수 없다`() {
        givenCourse(
            id = 1L,
            seatLeftCount = 10,
            capacity = 10,
            status = CourseStatus.CLOSED,
        )

        assertThatThrownBy {
            courseBulkWriter.reserveSeatsBulk(
                mapOf(1L to 1),
            )
        }.isInstanceOf(CourseInvalidStateException::class.java)

        assertSeatLeftCount(1L, 10)
    }

    @Test
    fun `여러 예약 요청 중 하나라도 OPEN 상태가 아니면 전체 예약을 롤백한다`() {
        givenCourse(id = 1L, seatLeftCount = 10, capacity = 10, status = CourseStatus.OPEN)
        givenCourse(id = 2L, seatLeftCount = 10, capacity = 10, status = CourseStatus.CLOSED)

        assertThatThrownBy {
            courseBulkWriter.reserveSeatsBulk(
                mapOf(
                    1L to 3,
                    2L to 2,
                ),
            )
        }.isInstanceOf(CourseInvalidStateException::class.java)

        assertSeatLeftCount(1L, 10)
        assertSeatLeftCount(2L, 10)
    }

    @Test
    fun `존재하지 않는 강의가 예약 요청에 포함되면 실패한다`() {
        givenCourse(id = 1L, seatLeftCount = 10, capacity = 10)

        assertThatThrownBy {
            courseBulkWriter.reserveSeatsBulk(
                mapOf(
                    1L to 3,
                    999L to 1,
                ),
            )
        }.isInstanceOf(CourseInvalidStateException::class.java)

        assertSeatLeftCount(1L, 10)
    }

    @Test
    fun `강의별 좌석을 서로 다른 수량으로 일괄 반환할 수 있다`() {
        givenCourse(id = 1L, seatLeftCount = 3, capacity = 10)
        givenCourse(id = 2L, seatLeftCount = 4, capacity = 10)
        givenCourse(id = 3L, seatLeftCount = 0, capacity = 10)

        courseBulkWriter.releaseSeatsBulk(
            mapOf(
                1L to 2,
                2L to 1,
                3L to 5,
            ),
        )

        assertSeatLeftCount(1L, 5)
        assertSeatLeftCount(2L, 5)
        assertSeatLeftCount(3L, 5)
    }

    @Test
    fun `capacity와 같아지는 좌석 반환은 허용한다`() {
        givenCourse(id = 1L, seatLeftCount = 7, capacity = 10)

        courseBulkWriter.releaseSeatsBulk(
            mapOf(1L to 3),
        )

        assertSeatLeftCount(1L, 10)
    }

    @Test
    fun `반환 수량이 0 이하인 요청은 무시한다`() {
        givenCourse(id = 1L, seatLeftCount = 5, capacity = 10)

        courseBulkWriter.releaseSeatsBulk(
            mapOf(
                1L to 0,
                2L to -1,
            ),
        )

        assertSeatLeftCount(1L, 5)
    }

    @Test
    fun `반환 요청 중 0 이하 수량은 무시하고 양수 수량만 처리한다`() {
        givenCourse(id = 1L, seatLeftCount = 5, capacity = 10)
        givenCourse(id = 2L, seatLeftCount = 5, capacity = 10)

        courseBulkWriter.releaseSeatsBulk(
            mapOf(
                1L to 2,
                2L to 0,
                3L to -1,
            ),
        )

        assertSeatLeftCount(1L, 7)
        assertSeatLeftCount(2L, 5)
    }

    @Test
    fun `빈 반환 요청은 아무 작업도 하지 않는다`() {
        givenCourse(id = 1L, seatLeftCount = 5, capacity = 10)

        courseBulkWriter.releaseSeatsBulk(emptyMap())

        assertSeatLeftCount(1L, 5)
    }

    @Test
    fun `capacity를 초과하여 좌석을 반환하려 하면 실패한다`() {
        givenCourse(id = 1L, seatLeftCount = 9, capacity = 10)

        assertThatThrownBy {
            courseBulkWriter.releaseSeatsBulk(
                mapOf(1L to 2),
            )
        }.isInstanceOf(CourseInvalidStateException::class.java)

        assertSeatLeftCount(1L, 9)
    }

    @Test
    fun `여러 반환 요청 중 하나라도 capacity를 초과하면 전체 반환을 롤백한다`() {
        givenCourse(id = 1L, seatLeftCount = 5, capacity = 10)
        givenCourse(id = 2L, seatLeftCount = 9, capacity = 10)

        assertThatThrownBy {
            courseBulkWriter.releaseSeatsBulk(
                mapOf(
                    1L to 3,
                    2L to 2,
                ),
            )
        }.isInstanceOf(CourseInvalidStateException::class.java)

        assertSeatLeftCount(1L, 5)
        assertSeatLeftCount(2L, 9)
    }

    @Test
    fun `존재하지 않는 강의가 반환 요청에 포함되면 실패한다`() {
        givenCourse(id = 1L, seatLeftCount = 5, capacity = 10)

        assertThatThrownBy {
            courseBulkWriter.releaseSeatsBulk(
                mapOf(
                    1L to 2,
                    999L to 1,
                ),
            )
        }.isInstanceOf(CourseInvalidStateException::class.java)

        assertSeatLeftCount(1L, 5)
    }

    @Test
    fun `OPEN 상태가 아닌 강의도 좌석 반환은 가능하다`() {
        givenCourse(
            id = 1L,
            seatLeftCount = 5,
            capacity = 10,
            status = CourseStatus.CLOSED,
        )

        courseBulkWriter.releaseSeatsBulk(
            mapOf(1L to 2),
        )

        assertSeatLeftCount(1L, 7)
    }

    private fun givenCourse(
        id: Long,
        seatLeftCount: Int,
        capacity: Int = 10,
        status: CourseStatus = CourseStatus.OPEN,
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
            "Kotlin Basic $id",
            "Kotlin course $id",
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
