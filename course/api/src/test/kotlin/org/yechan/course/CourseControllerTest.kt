package org.yechan.course

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.getBean
import org.springframework.boot.SpringBootConfiguration
import org.springframework.boot.autoconfigure.EnableAutoConfiguration
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.ApplicationContext
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Import
import org.springframework.context.annotation.Primary
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.security.access.hierarchicalroles.RoleHierarchy
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.test.context.support.WithMockUser
import org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers
import org.springframework.test.context.TestPropertySource
import org.springframework.test.web.servlet.client.RestTestClient
import org.springframework.test.web.servlet.setup.DefaultMockMvcBuilder
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import org.springframework.web.client.ApiVersionInserter
import org.springframework.web.context.WebApplicationContext
import org.yechan.ServiceAutoConfiguration
import org.yechan.TokenGenerator
import org.yechan.member.MemberRole
import org.yechan.member.MemberSecurityAdapterConfiguration
import java.time.LocalDateTime

@SpringBootTest(
    classes = [
        CourseControllerTest.TestApplication::class,
        CourseControllerTest.TestBeans::class,
    ],
)
@TestPropertySource(
    properties = [
        "auth.token.salt=test-salt",
        "auth.token.access-expires-in=1800",
        "auth.token.refresh-expires-in=604800",
    ],
)
class CourseControllerTest @Autowired constructor(
    private val restTestClient: RestTestClient,
    private val tokenGenerator: TokenGenerator,
    private val context: ApplicationContext,
) {
    @Test
    fun `강의 등록 모집 시작 마감 목록 상세 API를 제공한다`() {
        val accessToken =
            tokenGenerator.generate(1L, roles = setOf(MemberRole.CREATOR.name)).accessToken

        restTestClient.post()
            .uri("/api/courses")
            .header("X-API-Version", "v1")
            .header(HttpHeaders.AUTHORIZATION, "Bearer $accessToken")
            .contentType(MediaType.APPLICATION_JSON)
            .body(
                """
                {
                  "title": "Kotlin Basic",
                  "description": "Kotlin course",
                  "price": 100000,
                  "capacity": 2,
                  "periodStart": "2026-06-01T00:00:00",
                  "periodEnd": "2026-06-30T00:00:00"
                }
                """.trimIndent(),
            )
            .exchange()
            .expectStatus().isOk
            .expectBody()
            .jsonPath("$.capacity").isEqualTo(2)
            .jsonPath("$.seatLeftCount").isEqualTo(2)
            .jsonPath("$.status").isEqualTo("DRAFT")

        restTestClient.post()
            .uri("/api/courses/1/open")
            .header("X-API-Version", "v1")
            .header(HttpHeaders.AUTHORIZATION, "Bearer $accessToken")
            .exchange()
            .expectStatus().isOk
            .expectBody()
            .jsonPath("$.status").isEqualTo("OPEN")

        restTestClient.post()
            .uri("/api/courses/1/close")
            .header("X-API-Version", "v1")
            .header(HttpHeaders.AUTHORIZATION, "Bearer $accessToken")
            .exchange()
            .expectStatus().isOk
            .expectBody()
            .jsonPath("$.status").isEqualTo("CLOSED")

        restTestClient.get()
            .uri("/api/courses")
            .header("X-API-Version", "v1")
            .exchange()
            .expectStatus().isOk
            .expectBody()
            .jsonPath("$[0].capacity").isEqualTo(2)
            .jsonPath("$[0].seatLeftCount").isEqualTo(1)
            .jsonPath("$[0].currentEnrollmentCount").isEqualTo(1)

        restTestClient.get()
            .uri("/api/courses?status=OPEN")
            .header("X-API-Version", "v1")
            .exchange()
            .expectStatus().isOk
            .expectBody()
            .jsonPath("$[0].status").isEqualTo("OPEN")
            .jsonPath("$[0].currentEnrollmentCount").isEqualTo(1)

        restTestClient.get()
            .uri("/api/courses/1")
            .header("X-API-Version", "v1")
            .exchange()
            .expectStatus().isOk
            .expectBody()
            .jsonPath("$.capacity").isEqualTo(2)
            .jsonPath("$.seatLeftCount").isEqualTo(1)
            .jsonPath("$.currentEnrollmentCount").isEqualTo(1)
    }

    @Test
    fun `수강생은 강의 등록 모집 시작 마감 API를 사용할 수 없다`() {
        val accessToken =
            tokenGenerator.generate(2L, roles = setOf(MemberRole.CLASSMATE.name)).accessToken

        restTestClient.post()
            .uri("/api/courses")
            .header("X-API-Version", "v1")
            .header(HttpHeaders.AUTHORIZATION, "Bearer $accessToken")
            .contentType(MediaType.APPLICATION_JSON)
            .body(
                """
                {
                  "title": "Kotlin Basic",
                  "description": "Kotlin course",
                  "price": 100000,
                  "capacity": 2,
                  "periodStart": "2026-06-01T00:00:00",
                  "periodEnd": "2026-06-30T00:00:00"
                }
                """.trimIndent(),
            )
            .exchange()
            .expectStatus().isForbidden

        restTestClient.post()
            .uri("/api/courses/1/open")
            .header("X-API-Version", "v1")
            .header(HttpHeaders.AUTHORIZATION, "Bearer $accessToken")
            .exchange()
            .expectStatus().isForbidden

        restTestClient.post()
            .uri("/api/courses/1/close")
            .header("X-API-Version", "v1")
            .header(HttpHeaders.AUTHORIZATION, "Bearer $accessToken")
            .exchange()
            .expectStatus().isForbidden
    }

    @Test
    fun `토큰이 없으면 강의 등록 API를 사용할 수 없다`() {
        restTestClient.post()
            .uri("/api/courses")
            .header("X-API-Version", "v1")
            .contentType(MediaType.APPLICATION_JSON)
            .body(
                """
                {
                  "title": "Kotlin Basic",
                  "description": "Kotlin course",
                  "price": 100000,
                  "capacity": 2,
                  "periodStart": "2026-06-01T00:00:00",
                  "periodEnd": "2026-06-30T00:00:00"
                }
                """.trimIndent(),
            )
            .exchange()
            .expectStatus().isUnauthorized
    }

    @Test
    fun `CREATOR 역할은 CLASSMATE 권한을 상속한다`() {
        val roleHierarchy = context.getBean<RoleHierarchy>()

        val reachableAuthorities =
            roleHierarchy.getReachableGrantedAuthorities(
                listOf(SimpleGrantedAuthority("ROLE_CREATOR")),
            ).mapNotNull { it.authority }

        assertThat(reachableAuthorities).contains("ROLE_CLASSMATE")
    }

    @Test
    @WithMockUser(username = "admin", roles = ["CREATOR"])
    fun `CREATOR는 강의 등록 모집 시작 마감 API를 사용할 수 있다`() {
        val accessToken =
            tokenGenerator.generate(3L, roles = setOf(MemberRole.CREATOR.name)).accessToken

        restTestClient.post()
            .uri("/api/courses")
            .header("X-API-Version", "v1")
            .header(HttpHeaders.AUTHORIZATION, "Bearer $accessToken")
            .contentType(MediaType.APPLICATION_JSON)
            .body(
                """
                {
                  "title": "Kotlin Basic",
                  "description": "Kotlin course",
                  "price": 100000,
                  "capacity": 2,
                  "periodStart": "2026-06-01T00:00:00",
                  "periodEnd": "2026-06-30T00:00:00"
                }
                """.trimIndent(),
            )
            .exchange()
            .expectStatus().isOk
    }

    @SpringBootConfiguration
    @EnableAutoConfiguration(
        exclude = [
            ServiceAutoConfiguration::class,
            MemberSecurityAdapterConfiguration::class,
        ],
    )
    @Import(
        CourseController::class,
        CourseAuthorizationPolicy::class,
        CourseOpenEndpointPolicy::class,
    )
    class TestApplication

    @TestConfiguration
    class TestBeans {
        @Bean
        fun restTestClient(context: WebApplicationContext): RestTestClient {
            val mockMvc =
                MockMvcBuilders.webAppContextSetup(context)
                    .apply<DefaultMockMvcBuilder>(SecurityMockMvcConfigurers.springSecurity())
                    .build()
            return RestTestClient.bindTo(mockMvc)
                .apiVersionInserter(ApiVersionInserter.useHeader("X-API-Version"))
                .build()
        }

        @Bean
        @Primary
        fun courseUseCase(): CourseUseCase = FakeCourseUseCase()
    }

    class FakeCourseUseCase : CourseUseCase {
        override fun createCourse(command: CreateCourseCommand, creatorId: Long): CourseResult = course(status = CourseStatus.DRAFT)

        override fun openCourse(command: CourseStatusCommand): CourseResult = course(status = CourseStatus.OPEN)

        override fun closeCourse(command: CourseStatusCommand): CourseResult = course(status = CourseStatus.CLOSED)

        override fun getCourses(status: CourseStatus?): List<CourseResult> = when (status) {
            CourseStatus.OPEN -> listOf(course(status = CourseStatus.OPEN, seatLeftCount = 1))

            CourseStatus.CLOSED -> listOf(course(status = CourseStatus.CLOSED, seatLeftCount = 1))

            CourseStatus.DRAFT -> listOf(course(status = CourseStatus.DRAFT))

            null -> listOf(
                course(status = CourseStatus.OPEN, seatLeftCount = 1),
                course(status = CourseStatus.CLOSED, seatLeftCount = 1),
            )
        }

        override fun getCourse(courseId: Long): CourseResult = course(status = CourseStatus.CLOSED, seatLeftCount = 1)

        private fun course(
            status: CourseStatus,
            seatLeftCount: Int = 2,
        ) = CourseResult(
            courseId = 1L,
            creatorId = 1L,
            title = "Kotlin Basic",
            description = "Kotlin course",
            price = Money(100_000L),
            capacity = 2,
            seatLeftCount = seatLeftCount,
            currentEnrollmentCount = 2 - seatLeftCount,
            periodStart = LocalDateTime.of(2026, 6, 1, 0, 0),
            periodEnd = LocalDateTime.of(2026, 6, 30, 0, 0),
            status = status,
        )
    }
}
