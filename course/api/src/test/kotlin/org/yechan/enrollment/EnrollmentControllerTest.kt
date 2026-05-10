package org.yechan.enrollment

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
import org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers
import org.springframework.test.context.TestPropertySource
import org.springframework.test.web.servlet.client.RestTestClient
import org.springframework.test.web.servlet.setup.DefaultMockMvcBuilder
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import org.springframework.web.client.ApiVersionInserter
import org.springframework.web.context.WebApplicationContext
import org.yechan.ServiceAutoConfiguration
import org.yechan.TokenGenerator
import org.yechan.course.CourseAuthorizationPolicy
import org.yechan.member.CurrentMemberResult
import org.yechan.member.LoginCommand
import org.yechan.member.LoginResult
import org.yechan.member.LogoutCommand
import org.yechan.member.MemberAuthUseCase
import org.yechan.member.MemberRole
import org.yechan.member.MemberSecurityAdapterConfiguration
import org.yechan.member.MemberStatus
import org.yechan.member.RefreshTokenCommand
import org.yechan.member.RefreshTokenResult
import org.yechan.member.SignupCommand
import org.yechan.member.SignupResult

@SpringBootTest(
    classes = [
        EnrollmentControllerTest.TestApplication::class,
        EnrollmentControllerTest.TestBeans::class,
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

        @Bean
        fun memberAuthUseCase(): MemberAuthUseCase = object : MemberAuthUseCase {
            override fun signup(command: SignupCommand): SignupResult = throw UnsupportedOperationException()

            override fun login(command: LoginCommand): LoginResult = throw UnsupportedOperationException()

            override fun refresh(command: RefreshTokenCommand): RefreshTokenResult = throw UnsupportedOperationException()

            override fun logout(command: LogoutCommand) {
            }

            override fun getCurrentUser(userId: Long): CurrentMemberResult = CurrentMemberResult(
                id = userId,
                email = "user$userId@test.com",
                name = "user$userId",
                role = MemberRole.CREATOR,
                status = MemberStatus.ACTIVE,
            )

            override fun getCurrentUserByEmail(email: String): CurrentMemberResult = CurrentMemberResult(
                id = email.filter(Char::isDigit).toLongOrNull() ?: 1L,
                email = email,
                name = "test-user",
                role = MemberRole.CREATOR,
                status = MemberStatus.ACTIVE,
            )
        }
    }
}

class FakeEnrollmentUseCase : EnrollmentUseCase {
    override fun enroll(command: EnrollCourseCommand): EnrollmentResult = enrollment(
        memberId = command.memberId,
        status = EnrollmentStatus.PENDING,
    )

    override fun confirmEnrollment(command: EnrollmentStatusCommand): EnrollmentResult = enrollment(
        memberId = command.memberId,
        status = EnrollmentStatus.CONFIRMED,
    )

    override fun cancelEnrollment(command: EnrollmentStatusCommand): EnrollmentResult = enrollment(
        memberId = command.memberId,
        status = EnrollmentStatus.CANCELLED,
    )

    override fun getMyEnrollments(memberId: Long): List<EnrollmentResult> = listOf(
        enrollment(
            memberId = memberId,
            status = EnrollmentStatus.CANCELLED,
        ),
    )

    private fun enrollment(
        memberId: Long,
        status: EnrollmentStatus,
    ) = EnrollmentResult(
        enrollmentId = 1L,
        courseId = 1L,
        memberId = memberId,
        status = status,
    )
}
