package org.yechan.course

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
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
    @BeforeEach
    fun setUp() {
        context.getBean<FakeCourseUseCase>().reset()
    }

    @Test
    fun `강의 등록 모집 시작 마감 목록 상세 API를 제공한다`() {
        val accessToken =
            tokenGenerator.generate(1L, roles = setOf(MemberRole.CREATOR.name)).accessToken

        createCourse(
            accessToken = accessToken,
            title = "Kotlin Basic",
        )

        restTestClient.post()
            .uri("/api/courses/1/open")
            .header("X-API-Version", "v1")
            .header(HttpHeaders.AUTHORIZATION, "Bearer $accessToken")
            .exchange()
            .expectStatus().isOk
            .expectBody()
            .jsonPath("$.courseId").isEqualTo(1)
            .jsonPath("$.title").isEqualTo("Kotlin Basic")
            .jsonPath("$.capacity").isEqualTo(2)
            .jsonPath("$.seatLeftCount").isEqualTo(2)
            .jsonPath("$.currentEnrollmentCount").isEqualTo(0)
            .jsonPath("$.status").isEqualTo("OPEN")

        restTestClient.post()
            .uri("/api/courses/1/close")
            .header("X-API-Version", "v1")
            .header(HttpHeaders.AUTHORIZATION, "Bearer $accessToken")
            .exchange()
            .expectStatus().isOk
            .expectBody()
            .jsonPath("$.courseId").isEqualTo(1)
            .jsonPath("$.title").isEqualTo("Kotlin Basic")
            .jsonPath("$.capacity").isEqualTo(2)
            .jsonPath("$.seatLeftCount").isEqualTo(2)
            .jsonPath("$.currentEnrollmentCount").isEqualTo(0)
            .jsonPath("$.status").isEqualTo("CLOSED")

        restTestClient.get()
            .uri("/api/courses")
            .header("X-API-Version", "v1")
            .exchange()
            .expectStatus().isOk
            .expectBody()
            .jsonPath("$.length()").isEqualTo(1)
            .jsonPath("$[0].courseId").isEqualTo(1)
            .jsonPath("$[0].title").isEqualTo("Kotlin Basic")
            .jsonPath("$[0].capacity").isEqualTo(2)
            .jsonPath("$[0].seatLeftCount").isEqualTo(2)
            .jsonPath("$[0].currentEnrollmentCount").isEqualTo(0)
            .jsonPath("$[0].status").isEqualTo("CLOSED")

        restTestClient.get()
            .uri("/api/courses?status=CLOSED")
            .header("X-API-Version", "v1")
            .exchange()
            .expectStatus().isOk
            .expectBody()
            .jsonPath("$.length()").isEqualTo(1)
            .jsonPath("$[0].courseId").isEqualTo(1)
            .jsonPath("$[0].status").isEqualTo("CLOSED")

        restTestClient.get()
            .uri("/api/courses/1")
            .header("X-API-Version", "v1")
            .exchange()
            .expectStatus().isOk
            .expectBody()
            .jsonPath("$.courseId").isEqualTo(1)
            .jsonPath("$.title").isEqualTo("Kotlin Basic")
            .jsonPath("$.capacity").isEqualTo(2)
            .jsonPath("$.seatLeftCount").isEqualTo(2)
            .jsonPath("$.currentEnrollmentCount").isEqualTo(0)
            .jsonPath("$.status").isEqualTo("CLOSED")
    }

    @Test
    fun `강의 목록 조회는 상태 파라미터가 없으면 전체 강의를 조회한다`() {
        val accessToken =
            tokenGenerator.generate(1L, roles = setOf(MemberRole.CREATOR.name)).accessToken

        createCourse(accessToken = accessToken, title = "Draft Course")
        createCourse(accessToken = accessToken, title = "Open Course")
        createCourse(accessToken = accessToken, title = "Closed Course")

        openCourse(accessToken = accessToken, courseId = 2L)
        openCourse(accessToken = accessToken, courseId = 3L)
        closeCourse(accessToken = accessToken, courseId = 3L)

        restTestClient.get()
            .uri("/api/courses")
            .header("X-API-Version", "v1")
            .exchange()
            .expectStatus().isOk
            .expectBody()
            .jsonPath("$.length()").isEqualTo(3)
            .jsonPath("$[0].title").isEqualTo("Draft Course")
            .jsonPath("$[0].status").isEqualTo("DRAFT")
            .jsonPath("$[1].title").isEqualTo("Open Course")
            .jsonPath("$[1].status").isEqualTo("OPEN")
            .jsonPath("$[2].title").isEqualTo("Closed Course")
            .jsonPath("$[2].status").isEqualTo("CLOSED")
    }

    @Test
    fun `강의 목록 조회는 상태 파라미터로 필터링할 수 있다`() {
        val accessToken =
            tokenGenerator.generate(1L, roles = setOf(MemberRole.CREATOR.name)).accessToken

        createCourse(accessToken = accessToken, title = "Draft Course")
        createCourse(accessToken = accessToken, title = "Open Course")
        createCourse(accessToken = accessToken, title = "Closed Course")

        openCourse(accessToken = accessToken, courseId = 2L)
        openCourse(accessToken = accessToken, courseId = 3L)
        closeCourse(accessToken = accessToken, courseId = 3L)

        restTestClient.get()
            .uri("/api/courses?status=DRAFT")
            .header("X-API-Version", "v1")
            .exchange()
            .expectStatus().isOk
            .expectBody()
            .jsonPath("$.length()").isEqualTo(1)
            .jsonPath("$[0].title").isEqualTo("Draft Course")
            .jsonPath("$[0].status").isEqualTo("DRAFT")

        restTestClient.get()
            .uri("/api/courses?status=OPEN")
            .header("X-API-Version", "v1")
            .exchange()
            .expectStatus().isOk
            .expectBody()
            .jsonPath("$.length()").isEqualTo(1)
            .jsonPath("$[0].title").isEqualTo("Open Course")
            .jsonPath("$[0].status").isEqualTo("OPEN")

        restTestClient.get()
            .uri("/api/courses?status=CLOSED")
            .header("X-API-Version", "v1")
            .exchange()
            .expectStatus().isOk
            .expectBody()
            .jsonPath("$.length()").isEqualTo(1)
            .jsonPath("$[0].title").isEqualTo("Closed Course")
            .jsonPath("$[0].status").isEqualTo("CLOSED")
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
                  "periodStart": "2099-06-01T00:00:00",
                  "periodEnd": "2099-06-30T00:00:00"
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
                  "periodStart": "2099-06-01T00:00:00",
                  "periodEnd": "2099-06-30T00:00:00"
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

        createCourse(
            accessToken = accessToken,
            title = "Kotlin Basic",
        )
    }

    private fun createCourse(
        accessToken: String,
        title: String,
        capacity: Int = 2,
    ) {
        restTestClient.post()
            .uri("/api/courses")
            .header("X-API-Version", "v1")
            .header(HttpHeaders.AUTHORIZATION, "Bearer $accessToken")
            .contentType(MediaType.APPLICATION_JSON)
            .body(
                """
                {
                  "title": "$title",
                  "description": "$title description",
                  "price": 100000,
                  "capacity": $capacity,
                  "periodStart": "2099-06-01T00:00:00",
                  "periodEnd": "2099-06-30T00:00:00"
                }
                """.trimIndent(),
            )
            .exchange()
            .expectStatus().isOk
            .expectBody()
            .jsonPath("$.title").isEqualTo(title)
            .jsonPath("$.capacity").isEqualTo(capacity)
            .jsonPath("$.seatLeftCount").isEqualTo(capacity)
            .jsonPath("$.currentEnrollmentCount").isEqualTo(0)
            .jsonPath("$.status").isEqualTo("DRAFT")
    }

    private fun openCourse(
        accessToken: String,
        courseId: Long,
    ) {
        restTestClient.post()
            .uri("/api/courses/$courseId/open")
            .header("X-API-Version", "v1")
            .header(HttpHeaders.AUTHORIZATION, "Bearer $accessToken")
            .exchange()
            .expectStatus().isOk
            .expectBody()
            .jsonPath("$.courseId").isEqualTo(courseId)
            .jsonPath("$.status").isEqualTo("OPEN")
    }

    private fun closeCourse(
        accessToken: String,
        courseId: Long,
    ) {
        restTestClient.post()
            .uri("/api/courses/$courseId/close")
            .header("X-API-Version", "v1")
            .header(HttpHeaders.AUTHORIZATION, "Bearer $accessToken")
            .exchange()
            .expectStatus().isOk
            .expectBody()
            .jsonPath("$.courseId").isEqualTo(courseId)
            .jsonPath("$.status").isEqualTo("CLOSED")
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
        fun courseUseCase(): FakeCourseUseCase = FakeCourseUseCase()
    }

    class FakeCourseUseCase : CourseUseCase {
        private val courses = linkedMapOf<Long, CourseResult>()
        private var sequence = 1L

        fun reset() {
            courses.clear()
            sequence = 1L
        }

        override fun createCourse(
            command: CreateCourseCommand,
            creatorId: Long,
        ): CourseResult {
            val courseId = sequence++

            val course =
                CourseResult(
                    courseId = courseId,
                    creatorId = creatorId,
                    title = command.title,
                    description = command.description,
                    price = command.price,
                    capacity = command.capacity,
                    seatLeftCount = command.capacity,
                    currentEnrollmentCount = 0,
                    periodStart = command.periodStart,
                    periodEnd = command.periodEnd,
                    status = CourseStatus.DRAFT,
                )

            courses[courseId] = course

            return course
        }

        override fun openCourse(command: CourseStatusCommand): CourseResult {
            val course = findCourse(command.courseId)

            val opened = course.withStatus(CourseStatus.OPEN)

            courses[command.courseId] = opened

            return opened
        }

        override fun closeCourse(command: CourseStatusCommand): CourseResult {
            val course = findCourse(command.courseId)

            val closed = course.withStatus(CourseStatus.CLOSED)

            courses[command.courseId] = closed

            return closed
        }

        override fun getCourses(status: CourseStatus?): List<CourseResult> = courses.values
            .filter { course -> status == null || course.status == status }

        override fun getCourse(courseId: Long): CourseResult = findCourse(courseId)

        private fun findCourse(courseId: Long): CourseResult = courses[courseId]
            ?: throw IllegalArgumentException("강의를 찾을 수 없습니다.")

        private fun CourseResult.withStatus(status: CourseStatus): CourseResult = CourseResult(
            courseId = courseId,
            creatorId = creatorId,
            title = title,
            description = description,
            price = price,
            capacity = capacity,
            seatLeftCount = seatLeftCount,
            currentEnrollmentCount = currentEnrollmentCount,
            periodStart = periodStart,
            periodEnd = periodEnd,
            status = status,
        )
    }
}
