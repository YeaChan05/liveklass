package org.yechan.member

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

@SpringBootTest(
    classes = [
        MemberAuthControllerTest.TestApplication::class,
        MemberAuthControllerTest.TestBeans::class,
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
class MemberAuthControllerTest @Autowired constructor(
    private val restTestClient: RestTestClient,
    private val memberAuthService: FakeMemberAuthService,
    private val tokenGenerator: TokenGenerator,
) {
    @Test
    fun `회원가입은 요청을 검증하고 생성된 회원을 반환한다`() {
        restTestClient.post()
            .uri("/api/auth/signup")
            .header("X-API-Version", "v1")
            .contentType(MediaType.APPLICATION_JSON)
            .body(
                mapOf(
                    "email" to "invalid-email",
                    "password" to "short",
                    "name" to "김",
                    "role" to "ADMIN",
                ),
            )
            .exchange()
            .expectStatus().isBadRequest
            .expectBody<String>()
            .consumeWith { response ->
                val body = response.responseBody ?: ""
                assert(body.contains("올바른 이메일 형식이 아닙니다."))
                assert(body.contains("비밀번호는 8자 이상 64자 이하로 입력해야 합니다."))
                assert(body.contains("이름은 2자 이상 30자 이하로 입력해야 합니다."))
                assert(body.contains("가입 가능한 권한이 아닙니다."))
            }

        restTestClient.post()
            .uri("/api/auth/signup")
            .header("X-API-Version", "v1")
            .contentType(MediaType.APPLICATION_JSON)
            .body(
                mapOf(
                    "email" to "student@example.com",
                    "password" to "password1234!",
                    "name" to "홍길동",
                    "role" to "CLASSMATE",
                ),
            )
            .exchange()
            .expectStatus().isOk
            .expectBody()
            .jsonPath("$.userId").isEqualTo(1)
            .jsonPath("$.email").isEqualTo("student@example.com")
            .jsonPath("$.name").isEqualTo("홍길동")
            .jsonPath("$.role").isEqualTo("CLASSMATE")
    }

    @Test
    fun `로그인은 요청을 검증하고 인증 실패 사유를 숨긴다`() {
        val beforeLoginCalls = memberAuthService.loginCalls

        restTestClient.post()
            .uri("/api/auth/login")
            .header("X-API-Version", "v1")
            .contentType(MediaType.APPLICATION_JSON)
            .body(mapOf("email" to "invalid-email", "password" to ""))
            .exchange()
            .expectStatus().isBadRequest
            .expectBody<String>()
            .consumeWith { response ->
                val body = response.responseBody ?: ""
                assert(body.contains("이메일 또는 비밀번호가 올바르지 않습니다."))
            }
        assert(memberAuthService.loginCalls == beforeLoginCalls)

        restTestClient.post()
            .uri("/api/auth/login")
            .header("X-API-Version", "v1")
            .contentType(MediaType.APPLICATION_JSON)
            .body(mapOf("email" to "missing@example.com", "password" to "password1234!"))
            .exchange()
            .expectStatus().isUnauthorized
            .expectBody<String>()
            .isEqualTo("이메일 또는 비밀번호가 올바르지 않습니다.")
    }

    @Test
    fun `로그인 리프레시 내 정보 조회는 필요한 응답을 노출한다`() {
        val accessToken = tokenGenerator.generate(1L, roles = emptySet()).accessToken

        restTestClient.post()
            .uri("/api/auth/login")
            .header("X-API-Version", "v1")
            .contentType(MediaType.APPLICATION_JSON)
            .body(mapOf("email" to "student@example.com", "password" to "password1234!"))
            .exchange()
            .expectStatus().isOk
            .expectBody()
            .jsonPath("$.accessToken").isEqualTo("access-1")
            .jsonPath("$.refreshToken").isEqualTo("refresh-1")
            .jsonPath("$.tokenType").isEqualTo("Bearer")
            .jsonPath("$.expiresIn").isEqualTo(1800)
            .jsonPath("$.user.role").isEqualTo("CLASSMATE")

        restTestClient.post()
            .uri("/api/auth/token/refresh")
            .header("X-API-Version", "v1")
            .contentType(MediaType.APPLICATION_JSON)
            .body(mapOf("refreshToken" to "refresh-1"))
            .exchange()
            .expectStatus().isOk
            .expectBody()
            .jsonPath("$.accessToken").isEqualTo("access-1-new")
            .jsonPath("$.tokenType").isEqualTo("Bearer")
            .jsonPath("$.expiresIn").isEqualTo(1800)

        val beforeRefreshCalls = memberAuthService.refreshCalls
        restTestClient.post()
            .uri("/api/auth/token/refresh")
            .header("X-API-Version", "v1")
            .contentType(MediaType.APPLICATION_JSON)
            .body(mapOf("refreshToken" to ""))
            .exchange()
            .expectStatus().isBadRequest
            .expectBody<String>()
            .consumeWith { response ->
                val body = response.responseBody ?: ""
                assert(body.contains("유효하지 않은 Refresh Token입니다."))
            }
        assert(memberAuthService.refreshCalls == beforeRefreshCalls)

        restTestClient.get()
            .uri("/api/auth/me")
            .header("X-API-Version", "v1")
            .header(HttpHeaders.AUTHORIZATION, "Bearer $accessToken")
            .exchange()
            .expectStatus().isOk
            .expectBody()
            .jsonPath("$.id").isEqualTo(1)
            .jsonPath("$.email").isEqualTo("student@example.com")
            .jsonPath("$.name").isEqualTo("홍길동")
            .jsonPath("$.role").isEqualTo("CLASSMATE")
            .jsonPath("$.status").isEqualTo("ACTIVE")
    }

    @Test
    fun `로그아웃은 인증된 액세스 토큰을 전달한다`() {
        val accessToken = tokenGenerator.generate(1L, roles = emptySet()).accessToken

        restTestClient.post()
            .uri("/api/auth/logout")
            .header("X-API-Version", "v1")
            .header(HttpHeaders.AUTHORIZATION, "Bearer $accessToken")
            .exchange()
            .expectStatus().isNoContent

        assert(memberAuthService.logoutCalls == 1)
        assert(memberAuthService.logoutUserId == 1L)
        assert(memberAuthService.logoutAccessToken == accessToken)
    }

    @SpringBootConfiguration
    @EnableAutoConfiguration(
        exclude = [
            ServiceAutoConfiguration::class,
            MemberSecurityAdapterConfiguration::class,
        ],
    )
    @Import(
        MemberAuthController::class,
        MemberAuthOpenEndpointPolicy::class,
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
        fun memberAuthService(): FakeMemberAuthService = FakeMemberAuthService()
    }

    class FakeMemberAuthService : MemberAuthUseCase {
        var loginCalls = 0
            private set
        var refreshCalls = 0
            private set
        var logoutCalls = 0
            private set
        var logoutUserId: Long? = null
            private set
        var logoutAccessToken: String? = null
            private set

        override fun signup(command: SignupCommand): SignupResult = SignupResult(1, command.email, command.name.trim(), command.role)

        override fun login(command: LoginCommand): LoginResult {
            loginCalls += 1
            if (command.email != "student@example.com" || command.password != "password1234!") {
                throw MemberAuthenticationException()
            }
            return LoginResult(
                accessToken = "access-1",
                refreshToken = "refresh-1",
                tokenType = "Bearer",
                expiresIn = 1800,
                user = MemberSummary(1, "student@example.com", "홍길동", MemberRole.CLASSMATE),
            )
        }

        override fun refresh(command: RefreshTokenCommand): RefreshTokenResult {
            refreshCalls += 1
            return RefreshTokenResult("access-1-new", "Bearer", 1800)
        }

        override fun logout(command: LogoutCommand) {
            logoutCalls += 1
            logoutUserId = command.userId
            logoutAccessToken = command.accessToken
        }

        override fun getCurrentUser(userId: Long): CurrentMemberResult = CurrentMemberResult(
            id = userId,
            email = "student@example.com",
            name = "홍길동",
            role = MemberRole.CLASSMATE,
            status = MemberStatus.ACTIVE,
        )

        override fun getCurrentUserByEmail(email: String): CurrentMemberResult = getCurrentUser(1)
    }
}
