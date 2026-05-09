package org.yechan.enrollment

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.yechan.course.CourseEntity
import org.yechan.course.CourseModelData
import org.yechan.course.CourseStatus
import org.yechan.course.Money
import java.time.LocalDateTime

class EnrollmentEntityTest {
    @Test
    fun `수강 신청 모델로 엔티티를 생성한다`() {
        val course = courseEntity(courseId = 10L)
        val enrollment = EnrollmentModelData(
            enrollmentId = 30L,
            courseId = 10L,
            memberId = 20L,
            status = EnrollmentStatus.CONFIRMED,
        )

        val entity = EnrollmentEntity.from(enrollment, course.id!!)

        assertThat(entity.id).isEqualTo(30L)
        assertThat(entity.courseId).isEqualTo(10L)
        assertThat(entity.status).isEqualTo(EnrollmentStatus.CONFIRMED)
    }

    @Test
    fun `엔티티로 수강 신청 모델을 생성한다`() {
        val entity = EnrollmentEntity.from(
            EnrollmentModelData(
                enrollmentId = 30L,
                courseId = 10L,
                memberId = 20L,
                status = EnrollmentStatus.CANCELLED,
            ),
            courseId = 10L,
        )

        val enrollment = entity.toDomain()

        assertThat(enrollment.enrollmentId).isEqualTo(30L)
        assertThat(enrollment.courseId).isEqualTo(10L)
        assertThat(enrollment.status).isEqualTo(EnrollmentStatus.CANCELLED)
    }

    private fun courseEntity(courseId: Long): CourseEntity = CourseEntity.from(
        CourseModelData(
            courseId = courseId,
            creatorId = 1L,
            title = "Kotlin Basic",
            description = "Kotlin course",
            price = Money(100_000L),
            capacity = 10,
            periodStart = LocalDateTime.of(2026, 6, 1, 0, 0),
            periodEnd = LocalDateTime.of(2026, 6, 30, 0, 0),
            status = CourseStatus.OPEN,
        ),
        creatorId = 1L,
    )
}
