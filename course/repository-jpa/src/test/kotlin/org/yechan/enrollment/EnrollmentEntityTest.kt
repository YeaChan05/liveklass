package org.yechan.enrollment

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.yechan.course.CourseInvalidStateException

class EnrollmentEntityTest {

    @Test
    fun `수강 신청 모델로 엔티티를 생성할 수 있다`() {
        // arrange
        val enrollment = EnrollmentModelData(
            enrollmentId = 30L,
            courseId = 10L,
            memberId = 20L,
            status = EnrollmentStatus.CONFIRMED,
        )

        // act
        val entity = EnrollmentEntity.from(
            enrollment = enrollment,
        )

        // assert
        assertThat(entity.id).isEqualTo(30L)
        assertThat(entity.courseId).isEqualTo(10L)
        assertThat(entity.memberId).isEqualTo(20L)
        assertThat(entity.status).isEqualTo(EnrollmentStatus.CONFIRMED)
    }

    @Test
    fun `엔티티를 도메인 모델로 변환할 수 있다`() {
        // arrange
        val entity = EnrollmentEntity.from(
            enrollment = EnrollmentModelData(
                enrollmentId = 30L,
                courseId = 10L,
                memberId = 20L,
                status = EnrollmentStatus.CANCELLED,
            ),
        )

        // act
        val enrollment = entity.toDomain()

        // assert
        assertThat(enrollment.enrollmentId).isEqualTo(30L)
        assertThat(enrollment.courseId).isEqualTo(10L)
        assertThat(enrollment.memberId).isEqualTo(20L)
        assertThat(enrollment.status).isEqualTo(EnrollmentStatus.CANCELLED)
    }

    @Test
    fun `PENDING 상태의 수강 신청은 확정할 수 있다`() {
        // arrange
        val entity = enrollmentEntity(
            status = EnrollmentStatus.PENDING,
        )

        // act
        entity.confirm()

        // assert
        assertThat(entity.status).isEqualTo(EnrollmentStatus.CONFIRMED)
    }

    @Test
    fun `PENDING 상태가 아닌 수강 신청은 확정할 수 없다`() {
        // arrange
        val entity = enrollmentEntity(
            status = EnrollmentStatus.CONFIRMED,
        )

        // act & assert
        assertThatThrownBy {
            entity.confirm()
        }
            .isInstanceOf(CourseInvalidStateException::class.java)
            .hasMessage("결제 대기 상태의 신청만 확정할 수 있습니다.")
    }

    @Test
    fun `confirmPayment는 수강 신청을 확정한다`() {
        // arrange
        val entity = enrollmentEntity(
            status = EnrollmentStatus.PENDING,
        )

        // act
        entity.confirmPayment()

        // assert
        assertThat(entity.status).isEqualTo(EnrollmentStatus.CONFIRMED)
    }

    @Test
    fun `취소되지 않은 수강 신청은 취소할 수 있다`() {
        // arrange
        val entity = enrollmentEntity(
            status = EnrollmentStatus.PENDING,
        )

        // act
        entity.cancel()

        // assert
        assertThat(entity.status).isEqualTo(EnrollmentStatus.CANCELLED)
    }

    @Test
    fun `이미 취소된 수강 신청은 다시 취소할 수 없다`() {
        // arrange
        val entity = enrollmentEntity(
            status = EnrollmentStatus.CANCELLED,
        )

        // act & assert
        assertThatThrownBy {
            entity.cancel()
        }
            .isInstanceOf(CourseInvalidStateException::class.java)
            .hasMessage("결제 대기 상태에서만 취소가 가능합니다.")
    }

    private fun enrollmentEntity(
        enrollmentId: Long = 1L,
        courseId: Long = 10L,
        memberId: Long = 20L,
        status: EnrollmentStatus = EnrollmentStatus.PENDING,
    ): EnrollmentEntity = EnrollmentEntity.from(
        enrollment = EnrollmentModelData(
            enrollmentId = enrollmentId,
            courseId = courseId,
            memberId = memberId,
            status = status,
        ),
    )
}
