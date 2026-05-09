package org.yechan.course

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import java.time.LocalDateTime

class CourseEntityTest {

    @Test
    fun `DRAFT 상태의 Course는 모집이 가능하다`() {
        // Arrange
        val course = createCourse(status = CourseStatus.DRAFT)

        // Act
        course.open()

        // Assert
        assertThat(course.status).isEqualTo(CourseStatus.OPEN)
    }

    @Test
    fun `DRAFT 상태가 아닌 Course는 모집할 수 없다`() {
        // Arrange
        val course = createCourse(status = CourseStatus.OPEN)

        // Act & Assert
        assertThatThrownBy {
            course.open()
        }
            .isInstanceOf(CourseInvalidStateException::class.java)
            .hasMessage("초안 상태의 강의만 모집할 수 있습니다.")
    }

    @Test
    fun `OPEN 상태의 Course는 마감할 수 있다`() {
        // Arrange
        val course = createCourse(status = CourseStatus.OPEN)

        // Act
        course.close()

        // Assert
        assertThat(course.status).isEqualTo(CourseStatus.CLOSED)
    }

    @Test
    fun `OPEN 상태가 아닌 Course는 마감할 수 없다`() {
        // Arrange
        val course = createCourse(status = CourseStatus.DRAFT)

        // Act & Assert
        assertThatThrownBy {
            course.close()
        }
            .isInstanceOf(CourseInvalidStateException::class.java)
            .hasMessage("모집 중인 강의만 마감할 수 있습니다.")
    }

    @Test
    fun `OPEN 상태의 Course는 좌석을 예약할 수 있다`() {
        // Arrange
        val course = createCourse(
            status = CourseStatus.OPEN,
            seatLeftCount = 10,
        )

        // Act
        course.reserveSeat()

        // Assert
        assertThat(course.seatLeftCount).isEqualTo(9)
    }

    @Test
    fun `OPEN 상태가 아닌 Course는 좌석을 예약할 수 없다`() {
        // Arrange
        val course = createCourse(
            status = CourseStatus.DRAFT,
        )

        // Act & Assert
        assertThatThrownBy {
            course.reserveSeat()
        }
            .isInstanceOf(CourseInvalidStateException::class.java)
            .hasMessage("모집 중인 강의만 마감할 수 있습니다.")
    }

    @Test
    fun `잔여 좌석이 없는 Course는 좌석을 예약할 수 없다`() {
        // Arrange
        val course = createCourse(
            status = CourseStatus.OPEN,
            seatLeftCount = 0,
        )

        // Act & Assert
        assertThatThrownBy {
            course.reserveSeat()
        }
            .isInstanceOf(CourseInvalidStateException::class.java)
            .hasMessage("강의 정원을 초과할 수 없습니다.")
    }

    @Test
    fun `Course는 좌석을 반환할 수 있다`() {
        // Arrange
        val course = createCourse(
            capacity = 30,
            seatLeftCount = 10,
        )

        // Act
        course.releaseSeat()

        // Assert
        assertThat(course.seatLeftCount).isEqualTo(11)
    }

    @Test
    fun `잔여 좌석 수는 정원을 초과할 수 없다`() {
        // Arrange
        val course = createCourse(
            capacity = 30,
            seatLeftCount = 30,
        )

        // Act & Assert
        assertThatThrownBy {
            course.releaseSeat()
        }
            .isInstanceOf(CourseInvalidStateException::class.java)
            .hasMessage("남은 좌석 수는 정원을 초과할 수 없습니다.")
    }

    @Test
    fun `OPEN 상태의 Course는 수강 신청을 생성할 수 있다`() {
        // Arrange
        val course = createCourse(
            id = 1L,
            status = CourseStatus.OPEN,
        )

        // Act
        val enrollment = course.requestEnrollment(
            memberId = 10L,
            currentEnrollmentCount = 1,
        )

        // Assert
        assertThat(enrollment.courseId).isEqualTo(1L)
        assertThat(enrollment.memberId).isEqualTo(10L)
    }

    @Test
    fun `OPEN 상태가 아닌 Course는 수강 신청을 생성할 수 없다`() {
        // Arrange
        val course = createCourse(
            id = 1L,
            status = CourseStatus.DRAFT,
        )

        // Act & Assert
        assertThatThrownBy {
            course.requestEnrollment(
                memberId = 10L,
                currentEnrollmentCount = 1,
            )
        }
            .isInstanceOf(CourseInvalidStateException::class.java)
            .hasMessage("모집 중인 강의만 마감할 수 있습니다.")
    }

    @Test
    fun `정원을 초과한 경우 수강 신청을 생성할 수 없다`() {
        // Arrange
        val course = createCourse(
            id = 1L,
            status = CourseStatus.OPEN,
            capacity = 30,
        )

        // Act & Assert
        assertThatThrownBy {
            course.requestEnrollment(
                memberId = 10L,
                currentEnrollmentCount = 30,
            )
        }
            .isInstanceOf(CourseInvalidStateException::class.java)
            .hasMessage("강의 정원을 초과할 수 없습니다.")
    }

    @Test
    fun `저장되지 않은 Course는 수강 신청을 생성할 수 없다`() {
        // Arrange
        val course = createCourse(
            id = null,
            status = CourseStatus.OPEN,
        )

        // Act & Assert
        assertThatThrownBy {
            course.requestEnrollment(
                memberId = 10L,
                currentEnrollmentCount = 1,
            )
        }
            .isInstanceOf(CourseInvalidStateException::class.java)
            .hasMessage("저장된 강의만 신청할 수 있습니다.")
    }

    private fun createCourse(
        id: Long? = 1L,
        status: CourseStatus = CourseStatus.DRAFT,
        capacity: Int = 30,
        seatLeftCount: Int = 30,
    ): CourseEntity = CourseEntity.of(
        course = CourseModelData(
            courseId = id,
            creatorId = 1L,
            title = "테스트 강의",
            description = "테스트 설명",
            price = Money(10000),
            capacity = capacity,
            seatLeftCount = seatLeftCount,
            periodStart = LocalDateTime.now(),
            periodEnd = LocalDateTime.now().plusDays(30),
            status = status,
        ),
        creatorId = 1L,
    )
}
