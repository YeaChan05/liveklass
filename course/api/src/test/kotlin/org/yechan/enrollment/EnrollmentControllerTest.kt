package org.yechan.enrollment

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.SpringBootConfiguration
import org.springframework.boot.autoconfigure.EnableAutoConfiguration
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Import
import org.springframework.context.annotation.Primary
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers
import org.springframework.test.context.TestPropertySource
import org.springframework.test.web.servlet.client.RestTestClient
import org.springframework.test.web.servlet.client.expectBody
import org.springframework.test.web.servlet.setup.DefaultMockMvcBuilder
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import org.springframework.web.client.ApiVersionInserter
import org.springframework.web.context.WebApplicationContext
import org.yechan.ServiceAutoConfiguration
import org.yechan.TestMemberAuthUseCaseConfiguration
import org.yechan.TokenGenerator
import org.yechan.course.CourseAuthorizationPolicy
import org.yechan.member.MemberRole
import org.yechan.member.MemberSecurityAdapterConfiguration
import java.time.Instant

@SpringBootTest(
    classes = [
        EnrollmentControllerTest.TestApplication::class,
        EnrollmentControllerTest.TestBeans::class,
        TestMemberAuthUseCaseConfiguration::class,
    ],
)
@TestPropertySource(
    properties = [
        "auth.token.salt=test-salt",
        "auth.token.access-expires-in=1800",
        "auth.token.refresh-expires-in=604800",
    ],
)
class EnrollmentControllerTest @Autowired constructor(
    private val restTestClient: RestTestClient,
    private val tokenGenerator: TokenGenerator,
) {
    @Test
    fun `수강 신청 결제 확정 취소 내 신청 목록 API를 제공한다`() {
        val accessToken =
            tokenGenerator.generate(2L, roles = setOf(MemberRole.CLASSMATE.name)).accessToken

        restTestClient.post()
            .uri("/api/courses/1/enrollments")
            .header("X-API-Version", "v1")
            .header(HttpHeaders.AUTHORIZATION, "Bearer $accessToken")
            .exchange()
            .expectStatus().isOk
            .expectBody()
            .jsonPath("$.enrollmentId").isEqualTo(1)
            .jsonPath("$.status").isEqualTo("PENDING")

        restTestClient.post()
            .uri("/api/enrollments/1/confirm")
            .header("X-API-Version", "v1")
            .header(HttpHeaders.AUTHORIZATION, "Bearer $accessToken")
            .exchange()
            .expectStatus().isOk
            .expectBody()
            .jsonPath("$.status").isEqualTo("CONFIRMED")

        restTestClient.post()
            .uri("/api/enrollments/1/cancel")
            .header("X-API-Version", "v1")
            .header(HttpHeaders.AUTHORIZATION, "Bearer $accessToken")
            .exchange()
            .expectStatus().isOk
            .expectBody()
            .jsonPath("$.status").isEqualTo("CANCELLED")

        restTestClient.get()
            .uri("/api/enrollments/me")
            .header("X-API-Version", "v1")
            .header(HttpHeaders.AUTHORIZATION, "Bearer $accessToken")
            .exchange()
            .expectStatus().isOk
            .expectBody()
            .jsonPath("$[0].memberId").isEqualTo(2)
            .jsonPath("$[0].status").isEqualTo("CANCELLED")
    }

    @Test
    fun `정원이 가득 찬 경우 대기열 등록 응답을 반환한다`() {
        val accessToken =
            tokenGenerator.generate(2L, roles = setOf(MemberRole.CLASSMATE.name)).accessToken

        restTestClient.post()
            .uri("/api/courses/999/enrollments")
            .header("X-API-Version", "v1")
            .header(HttpHeaders.AUTHORIZATION, "Bearer $accessToken")
            .exchange()
            .expectStatus().isOk
            .expectBody<String>()
            .value { body ->
                assertThat(body).contains("\"enrollmentId\":null")
                assertThat(body).contains("\"memberId\":2")
                assertThat(body).contains("\"status\":\"WAITLISTED\"")
            }
    }

    @Test
    fun `대기열 조회 SSE 응답을 반환한다`() {
        val accessToken =
            tokenGenerator.generate(2L, roles = setOf(MemberRole.CLASSMATE.name)).accessToken

        restTestClient.get()
            .uri("/api/enrollments/waitlist/me")
            .header("X-API-Version", "v1")
            .header(HttpHeaders.AUTHORIZATION, "Bearer $accessToken")
            .exchange()
            .expectStatus().isOk
            .expectHeader().contentTypeCompatibleWith(MediaType.TEXT_EVENT_STREAM)
            .expectBody(String::class.java)
            .value { body ->
                assertThat(body).contains("\"courseId\":999")
                assertThat(body).contains("\"memberId\":2")
                assertThat(body).contains("data:")
            }
    }

    @Test
    fun `대기열 취소 API를 제공한다`() {
        val accessToken =
            tokenGenerator.generate(2L, roles = setOf(MemberRole.CLASSMATE.name)).accessToken

        restTestClient.delete()
            .uri("/api/enrollments/waitlist/999")
            .header("X-API-Version", "v1")
            .header(HttpHeaders.AUTHORIZATION, "Bearer $accessToken")
            .exchange()
            .expectStatus().isNoContent
    }

    @Test
    fun `CREATOR도 수강 신청 API를 사용할 수 있다`() {
        val accessToken =
            tokenGenerator.generate(1L, roles = setOf(MemberRole.CREATOR.name)).accessToken

        restTestClient.post()
            .uri("/api/courses/1/enrollments")
            .header("X-API-Version", "v1")
            .header(HttpHeaders.AUTHORIZATION, "Bearer $accessToken")
            .exchange()
            .expectStatus().isOk
            .expectBody()
            .jsonPath("$.memberId").isEqualTo(1)
            .jsonPath("$.status").isEqualTo("PENDING")
    }

    @Test
    fun `토큰이 없으면 수강 신청 API를 사용할 수 없다`() {
        restTestClient.post()
            .uri("/api/courses/1/enrollments")
            .header("X-API-Version", "v1")
            .exchange()
            .expectStatus().isUnauthorized
    }

    @SpringBootConfiguration
    @EnableAutoConfiguration(
        exclude = [
            ServiceAutoConfiguration::class,
            MemberSecurityAdapterConfiguration::class,
        ],
    )
    @Import(
        CourseAuthorizationPolicy::class,
        EnrollmentController::class,
        EnrollmentWaitlistSseHandler::class,
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
        fun enrollmentUseCase(): EnrollmentUseCase = FakeEnrollmentUseCase()
    }
}

class FakeEnrollmentUseCase : EnrollmentUseCase {
    override fun enroll(command: EnrollCourseCommand): EnrollResult = if (command.courseId == 999L) {
        EnrollResult.Waitlisted(
            courseId = command.courseId,
            memberId = command.memberId,
        )
    } else {
        EnrollResult.Enrolled(
            enrollment(
                memberId = command.memberId,
                status = EnrollmentStatus.PENDING,
            ),
        )
    }

    override fun confirmEnrollment(command: EnrollmentStatusCommand): EnrollmentInfo = enrollment(
        memberId = command.memberId,
        status = EnrollmentStatus.CONFIRMED,
    )

    override fun cancelEnrollment(command: EnrollmentStatusCommand): EnrollmentInfo = enrollment(
        memberId = command.memberId,
        status = EnrollmentStatus.CANCELLED,
    )

    override fun cancelWaitlist(command: EnrollmentWaitlistCommand) {
        Unit
    }

    override fun getMyEnrollments(memberId: Long): List<EnrollmentInfo> = listOf(
        enrollment(
            memberId = memberId,
            status = EnrollmentStatus.CANCELLED,
        ),
    )

    override fun getMyWaitlist(memberId: Long): List<WaitlistInfo> = if (memberId == 2L) {
        listOf(
            WaitlistInfo(
                courseId = 999L,
                memberId = memberId,
                requestedAt = Instant.parse("2026-01-01T00:00:00Z"),
            ),
        )
    } else {
        emptyList()
    }

    private fun enrollment(
        memberId: Long,
        status: EnrollmentStatus,
    ) = EnrollmentInfo(
        enrollmentId = 1L,
        courseId = 1L,
        memberId = memberId,
        status = status,
    )
}
