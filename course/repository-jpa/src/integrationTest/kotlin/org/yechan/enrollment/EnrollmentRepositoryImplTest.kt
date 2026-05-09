package org.yechan.enrollment

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
import org.yechan.course.CourseEntity
import org.yechan.course.CourseJpaRepository
import org.yechan.course.CourseModelData
import org.yechan.course.CourseStatus
import org.yechan.course.Money
import org.yechan.member.MemberEntity
import org.yechan.member.MemberJpaRepository
import org.yechan.member.MemberModelData
import org.yechan.member.MemberRole
import java.time.LocalDateTime

@DataJpaTest
@Import(EnrollmentRepositoryImpl::class)
@ContextConfiguration(classes = [EnrollmentRepositoryImplTest.TestApplication::class])
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
class EnrollmentRepositoryImplTest {
    @Autowired
    private lateinit var enrollmentRepository: EnrollmentRepositoryImpl

    @Autowired
    private lateinit var entityManager: EntityManager

    @BeforeEach
    fun reset() {
        entityManager.createNativeQuery("delete from enrollments").executeUpdate()
        entityManager.createNativeQuery("delete from courses").executeUpdate()
        entityManager.createNativeQuery("delete from members").executeUpdate()
        entityManager.flush()
        entityManager.clear()
    }

    @Test
    fun `수강 신청 저장은 강의와 회원 참조를 연결하고 생성된 아이디를 반환한다`() {
        val creator = persistMember(email = "creator@example.com", role = MemberRole.CREATOR)
        val member = persistMember(email = "student@example.com")
        val course = persistCourse(creator)

        val saved = enrollmentRepository.save(
            EnrollmentModelData(
                courseId = course.id!!,
                memberId = member.id!!,
                status = EnrollmentStatus.PENDING,
            ),
            courseId = course.id!!,
        )

        assertThat(saved.enrollmentId).isNotNull()
        assertThat(saved.courseId).isEqualTo(course.id)
        assertThat(saved.memberId).isEqualTo(member.id)
        assertThat(saved.status).isEqualTo(EnrollmentStatus.PENDING)
    }

    @Test
    fun `아이디 조회는 저장된 수강 신청을 반환한다`() {
        val creator = persistMember(email = "creator@example.com", role = MemberRole.CREATOR)
        val member = persistMember(email = "student@example.com")
        val course = persistCourse(creator)
        val saved = enrollmentRepository.save(
            EnrollmentModelData(
                courseId = course.id!!,
                memberId = member.id!!,
                status = EnrollmentStatus.CONFIRMED,
            ),
            courseId = course.id!!,
        )

        val found = enrollmentRepository.findById(saved.enrollmentId!!)

        assertThat(found).isEqualTo(saved)
    }

    @Test
    fun `알 수 없는 아이디 조회는 빈 값을 반환한다`() {
        assertThat(enrollmentRepository.findById(-1L)).isNull()
    }

    @Test
    fun `회원 아이디 조회는 해당 회원의 수강 신청만 반환한다`() {
        val creator = persistMember(email = "creator@example.com", role = MemberRole.CREATOR)
        val member = persistMember(email = "student@example.com")
        val otherMember = persistMember(email = "other@example.com")
        val course = persistCourse(creator)
        val memberEnrollment = enrollmentRepository.save(
            EnrollmentModelData(courseId = course.id!!, memberId = member.id!!),
            courseId = course.id!!,
        )
        enrollmentRepository.save(
            EnrollmentModelData(courseId = course.id!!, memberId = otherMember.id!!),
            courseId = course.id!!,
        )

        val enrollments = enrollmentRepository.findByMemberId(member.id!!)

        assertThat(enrollments).containsExactly(memberEnrollment)
    }

    private fun persistMember(
        email: String,
        role: MemberRole = MemberRole.CLASSMATE,
    ): MemberEntity {
        val member = MemberEntity.from(
            MemberModelData(
                email = email,
                passwordHash = "hashed-password",
                name = email.substringBefore("@"),
                role = role,
            ),
        )
        entityManager.persist(member)
        entityManager.flush()
        return member
    }

    private fun persistCourse(creator: MemberEntity): CourseEntity {
        val course = CourseEntity.from(
            CourseModelData(
                creatorId = creator.id,
                title = "Kotlin Basic",
                description = "Kotlin course",
                price = Money(100_000L),
                capacity = 10,
                periodStart = LocalDateTime.of(2026, 6, 1, 0, 0),
                periodEnd = LocalDateTime.of(2026, 6, 30, 0, 0),
                status = CourseStatus.OPEN,
            ),
            creatorId = creator.id!!,
        )
        entityManager.persist(course)
        entityManager.flush()
        entityManager.clear()
        return course
    }

    @SpringBootConfiguration
    @EnableAutoConfiguration
    @EntityScan(basePackageClasses = [MemberEntity::class, CourseEntity::class, EnrollmentEntity::class])
    @EnableJpaRepositories(
        basePackageClasses = [
            MemberJpaRepository::class,
            CourseJpaRepository::class,
            EnrollmentJpaRepository::class,
        ],
    )
    class TestApplication
}
