package org.yechan.course

import jakarta.persistence.EntityManager
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.SpringBootConfiguration
import org.springframework.boot.autoconfigure.EnableAutoConfiguration
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase
import org.springframework.boot.persistence.autoconfigure.EntityScan
import org.springframework.context.annotation.Import
import org.springframework.data.jpa.repository.config.EnableJpaRepositories
import org.springframework.test.context.ContextConfiguration
import org.springframework.test.context.TestPropertySource
import org.yechan.enrollment.EnrollmentEntity
import org.yechan.member.MemberEntity
import java.time.LocalDateTime

@DataJpaTest
@Import(CourseRepositoryImpl::class)
@ContextConfiguration(classes = [CourseRepositoryImplTest.TestApplication::class])
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@TestPropertySource(
    properties = [
        "spring.datasource.url=jdbc:tc:mysql:8.4.8://localhost:3306/course_repository_test?useSSL=false&serverTimezone=UTC&TC_DAEMON=true",
        "spring.datasource.driver-class-name=org.testcontainers.jdbc.ContainerDatabaseDriver",
        "spring.datasource.username=root",
        "spring.datasource.password=password",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.liquibase.enabled=false",
    ],
)
class CourseRepositoryImplTest {
    @Autowired
    private lateinit var courseRepository: CourseRepositoryImpl

    @Autowired
    private lateinit var entityManager: EntityManager

    @BeforeEach
    fun reset() {
        entityManager.createNativeQuery("delete from courses").executeUpdate()
        entityManager.flush()
        entityManager.clear()
    }

    @Test
    fun `강의 저장은 강의를 영속화하고 도메인 모델로 변환하여 반환한다`() {
        val course = course()
        val saved = courseRepository.save(course)

        assertThat(saved.courseId).isNotNull()
        assertThat(saved.title).isEqualTo(course.title)
        assertThat(saved.creatorId).isEqualTo(course.creatorId)
        assertThat(saved.price).isEqualTo(course.price)
    }

    @Test
    fun `아이디로 강의를 조회할 수 있다`() {
        val saved = courseRepository.save(course())

        val found = courseRepository.findById(saved.courseId!!)

        assertThat(found).isNotNull
        assertThat(found?.courseId).isEqualTo(saved.courseId)
        assertThat(found?.title).isEqualTo(saved.title)
    }

    @Test
    fun `존재하지 않는 아이디로 조회하면 null을 반환한다`() {
        assertThat(courseRepository.findById(-1L)).isNull()
    }

    @Test
    fun `모든 강의를 조회할 수 있다`() {
        courseRepository.save(course(title = "강의 1"))
        courseRepository.save(course(title = "강의 2"))

        val all = courseRepository.findAll()

        assertThat(all).hasSize(2)
        assertThat(all.map { it.title }).containsExactlyInAnyOrder("강의 1", "강의 2")
    }

    @Test
    fun `상태로 강의 목록을 조회할 수 있다`() {
        courseRepository.save(course(title = "초안 강의", status = CourseStatus.DRAFT))
        courseRepository.save(course(title = "모집 중 강의", status = CourseStatus.OPEN))
        courseRepository.save(course(title = "마감 강의", status = CourseStatus.CLOSED))

        val openCourses = courseRepository.findAllByStatus(CourseStatus.OPEN)

        assertThat(openCourses).hasSize(1)
        assertThat(openCourses[0].title).isEqualTo("모집 중 강의")
        assertThat(openCourses[0].status).isEqualTo(CourseStatus.OPEN)
    }

    @Test
    fun `모집 중이고 남은 좌석이 있으면 좌석을 선점할 수 있다`() {
        val saved = courseRepository.save(
            course(
                status = CourseStatus.OPEN,
                capacity = 30,
                seatLeftCount = 10,
            ),
        )

        entityManager.flush()
        entityManager.clear()

        val reserved = courseRepository.reserveSeatIfAvailable(saved.courseId!!)

        entityManager.flush()
        entityManager.clear()

        val found = courseRepository.findById(saved.courseId!!)

        assertThat(reserved).isTrue()
        assertThat(found?.seatLeftCount).isEqualTo(9)
    }

    @Test
    fun `초안 상태 강의는 좌석을 선점할 수 없다`() {
        val saved = courseRepository.save(
            course(
                status = CourseStatus.DRAFT,
                capacity = 30,
                seatLeftCount = 10,
            ),
        )

        entityManager.flush()
        entityManager.clear()

        val reserved = courseRepository.reserveSeatIfAvailable(saved.courseId!!)

        entityManager.flush()
        entityManager.clear()

        val found = courseRepository.findById(saved.courseId!!)

        assertThat(reserved).isFalse()
        assertThat(found?.seatLeftCount).isEqualTo(10)
    }

    @Test
    fun `마감 상태 강의는 좌석을 선점할 수 없다`() {
        val saved = courseRepository.save(
            course(
                status = CourseStatus.CLOSED,
                capacity = 30,
                seatLeftCount = 10,
            ),
        )

        entityManager.flush()
        entityManager.clear()

        val reserved = courseRepository.reserveSeatIfAvailable(saved.courseId!!)

        entityManager.flush()
        entityManager.clear()

        val found = courseRepository.findById(saved.courseId!!)

        assertThat(reserved).isFalse()
        assertThat(found?.seatLeftCount).isEqualTo(10)
    }

    @Test
    fun `남은 좌석이 없으면 좌석을 선점할 수 없다`() {
        val saved = courseRepository.save(
            course(
                status = CourseStatus.OPEN,
                capacity = 30,
                seatLeftCount = 0,
            ),
        )

        entityManager.flush()
        entityManager.clear()

        val reserved = courseRepository.reserveSeatIfAvailable(saved.courseId!!)

        entityManager.flush()
        entityManager.clear()

        val found = courseRepository.findById(saved.courseId!!)

        assertThat(reserved).isFalse()
        assertThat(found?.seatLeftCount).isEqualTo(0)
    }

    @Test
    fun `존재하지 않는 강의는 좌석을 선점할 수 없다`() {
        val reserved = courseRepository.reserveSeatIfAvailable(-1L)

        assertThat(reserved).isFalse()
    }

    @Test
    fun `좌석 수가 정원보다 작으면 좌석을 반환할 수 있다`() {
        val saved = courseRepository.save(
            course(
                status = CourseStatus.OPEN,
                capacity = 30,
                seatLeftCount = 10,
            ),
        )

        entityManager.flush()
        entityManager.clear()

        val released = courseRepository.releaseSeatIfPossible(saved.courseId!!)

        entityManager.flush()
        entityManager.clear()

        val found = courseRepository.findById(saved.courseId!!)

        assertThat(released).isTrue()
        assertThat(found?.seatLeftCount).isEqualTo(11)
    }

    @Test
    fun `좌석 수가 이미 정원과 같으면 좌석을 반환할 수 없다`() {
        val saved = courseRepository.save(
            course(
                status = CourseStatus.OPEN,
                capacity = 30,
                seatLeftCount = 30,
            ),
        )

        entityManager.flush()
        entityManager.clear()

        val released = courseRepository.releaseSeatIfPossible(saved.courseId!!)

        entityManager.flush()
        entityManager.clear()

        val found = courseRepository.findById(saved.courseId!!)

        assertThat(released).isFalse()
        assertThat(found?.seatLeftCount).isEqualTo(30)
    }

    @Test
    fun `존재하지 않는 강의는 좌석을 반환할 수 없다`() {
        val released = courseRepository.releaseSeatIfPossible(-1L)

        assertThat(released).isFalse()
    }

    private fun course(
        title: String = "테스트 강의",
        creatorId: Long = 1L,
        seatLeftCount: Int = 30,
        capacity: Int = 30,
        status: CourseStatus = CourseStatus.OPEN,
    ) = CourseModelData(
        title = title,
        description = "테스트 설명",
        creatorId = creatorId,
        price = Money(10000),
        capacity = capacity,
        status = status,
        seatLeftCount = seatLeftCount,
        periodStart = LocalDateTime.now().plusDays(1),
        periodEnd = LocalDateTime.now().plusDays(7),
    )

    @SpringBootConfiguration
    @EnableAutoConfiguration
    @EntityScan(basePackageClasses = [CourseEntity::class, MemberEntity::class, EnrollmentEntity::class])
    @EnableJpaRepositories(basePackageClasses = [CourseJpaRepository::class])
    class TestApplication
}
