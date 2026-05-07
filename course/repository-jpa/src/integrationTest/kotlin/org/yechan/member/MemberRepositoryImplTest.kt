package org.yechan.member

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
import org.yechan.enrollment.EnrollmentEntity

@DataJpaTest
@Import(MemberRepositoryImpl::class)
@ContextConfiguration(classes = [MemberRepositoryImplTest.TestApplication::class])
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
class MemberRepositoryImplTest {
    @Autowired
    private lateinit var memberRepository: MemberRepositoryImpl

    @Autowired
    private lateinit var entityManager: EntityManager

    @BeforeEach
    fun reset() {
        entityManager.createNativeQuery("delete from members").executeUpdate()
        entityManager.flush()
        entityManager.clear()
    }

    @Test
    fun `회원 저장은 회원을 영속화하고 생성된 아이디를 반환한다`() {
        val saved = memberRepository.save(member())

        assertThat(saved.memberId).isNotNull()
        assertThat(saved.email).isEqualTo("student@example.com")
        assertThat(saved.passwordHash).isEqualTo("hashed-password")
        assertThat(saved.name).isEqualTo("홍길동")
        assertThat(saved.role).isEqualTo(MemberRole.CLASSMATE)
        assertThat(saved.status).isEqualTo(MemberStatus.ACTIVE)
    }

    @Test
    fun `이메일 존재 여부는 저장된 이메일에만 참을 반환한다`() {
        memberRepository.save(member(email = "student@example.com"))

        assertThat(memberRepository.existsByEmail("student@example.com")).isTrue()
        assertThat(memberRepository.existsByEmail("unknown@example.com")).isFalse()
    }

    @Test
    fun `이메일 조회는 저장된 회원을 반환한다`() {
        val saved = memberRepository.save(member(email = "creator@example.com", role = MemberRole.CREATOR))

        val found = memberRepository.findByEmail("creator@example.com")

        assertThat(found).isEqualTo(saved)
    }

    @Test
    fun `알 수 없는 이메일 조회는 빈 값을 반환한다`() {
        assertThat(memberRepository.findByEmail("unknown@example.com")).isNull()
    }

    @Test
    fun `아이디 조회는 저장된 회원을 반환한다`() {
        val saved = memberRepository.save(member(status = MemberStatus.DELETED))

        val found = memberRepository.findById(saved.memberId!!)

        assertThat(found).isEqualTo(saved)
    }

    @Test
    fun `알 수 없는 아이디 조회는 빈 값을 반환한다`() {
        assertThat(memberRepository.findById(-1L)).isNull()
    }

    private fun member(
        email: String = "student@example.com",
        role: MemberRole = MemberRole.CLASSMATE,
        status: MemberStatus = MemberStatus.ACTIVE,
    ) = MemberModel(
        email = email,
        passwordHash = "hashed-password",
        name = "홍길동",
        role = role,
        status = status,
    )

    @SpringBootConfiguration
    @EnableAutoConfiguration
    @EntityScan(basePackageClasses = [MemberEntity::class, CourseEntity::class, EnrollmentEntity::class])
    @EnableJpaRepositories(basePackageClasses = [MemberJpaRepository::class])
    class TestApplication
}
