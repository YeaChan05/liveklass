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

    private fun course(
        title: String = "테스트 강의",
        creatorId: Long = 1L,
    ) = CourseModel(
        title = title,
        description = "테스트 설명",
        creatorId = creatorId,
        price = Money(10000),
        capacity = 30,
        periodStart = LocalDateTime.now().plusDays(1),
        periodEnd = LocalDateTime.now().plusDays(7),
    )

    @SpringBootConfiguration
    @EnableAutoConfiguration
    @EntityScan(basePackageClasses = [CourseEntity::class, MemberEntity::class, EnrollmentEntity::class])
    @EnableJpaRepositories(basePackageClasses = [CourseJpaRepository::class])
    class TestApplication
}
