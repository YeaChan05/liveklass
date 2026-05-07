package org.yechan

import org.assertj.core.api.Assertions
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.boot.SpringBootConfiguration
import org.springframework.boot.autoconfigure.EnableAutoConfiguration
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.boot.web.servlet.FilterRegistrationBean
import org.springframework.context.ApplicationContext
import org.springframework.context.annotation.Bean
import org.springframework.http.HttpHeaders
import org.springframework.security.core.Authentication
import org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers
import org.springframework.test.context.TestPropertySource
import org.springframework.test.web.servlet.client.RestTestClient
import org.springframework.test.web.servlet.client.expectBody
import org.springframework.test.web.servlet.setup.DefaultMockMvcBuilder
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.client.ApiVersionInserter
import org.springframework.web.context.WebApplicationContext
import java.time.Duration

@SpringBootTest(
    classes = [
        CommonSecurityAutoConfigurationTest.TestApplication::class,
        CommonSecurityAutoConfigurationTest.RestTestClientConfiguration::class,
    ],
)
@TestPropertySource(
    properties = [
        "auth.token.salt=test-salt",
        "auth.token.access-expires-in=3600",
        "auth.token.refresh-expires-in=7200",
    ],
)
class CommonSecurityAutoConfigurationTest {
    @Autowired
    lateinit var restTestClient: RestTestClient

    @Autowired
    @Qualifier("rawRestTestClient")
    lateinit var rawRestTestClient: RestTestClient

    @Autowired
    lateinit var tokenGenerator: TokenGenerator

    @Autowired
    lateinit var context: ApplicationContext

    @BeforeEach
    fun setUp() {
    }

    @Test
    fun `토큰이 없는 요청은 인증 실패로 거부된다`() {
        restTestClient.get()
            .uri("/secure")
            .header("X-API-Version", "v1")
            .exchange()
            .expectStatus().isUnauthorized
    }

    @Test
    fun `잘못된 토큰 요청은 인증 실패로 거부된다`() {
        restTestClient.get()
            .uri("/secure")
            .header("X-API-Version", "v1")
            .header(HttpHeaders.AUTHORIZATION, "Bearer invalid")
            .exchange()
            .expectStatus().isUnauthorized
    }

    @Test
    fun `올바른 토큰 요청은 허용된다`() {
        val token = tokenGenerator.generate(1L).accessToken

        restTestClient.get()
            .uri("/secure")
            .header("X-API-Version", "v1")
            .header(HttpHeaders.AUTHORIZATION, "Bearer $token")
            .exchange()
            .expectStatus().isOk
            .expectBody<String>()
            .isEqualTo("1")
    }

    @Test
    fun `블랙리스트에 등록된 토큰 요청은 인증 실패로 거부된다`() {
        val token = tokenGenerator.generate(1L).accessToken
        context.getBean(AccessTokenBlacklist::class.java).blacklist(token, Duration.ofMinutes(10))

        restTestClient.get()
            .uri("/secure")
            .header("X-API-Version", "v1")
            .header(HttpHeaders.AUTHORIZATION, "Bearer $token")
            .exchange()
            .expectStatus().isUnauthorized
    }

    @Test
    fun `보안 필터는 보안 체인에만 등록된다`() {
        val filterRegistrations = context.getBeansOfType(FilterRegistrationBean::class.java).values

        Assertions.assertThat(filterRegistrations)
            .filteredOn { it.filter is JwtAuthenticationFilter }
            .allMatch { !it.isEnabled }
            .hasSize(1)
    }

    @Test
    fun `추가 보안 정책은 기본 차단 규칙보다 먼저 적용된다`() {
        restTestClient.get()
            .uri("/open")
            .header("X-API-Version", "v1")
            .exchange()
            .expectStatus().isOk
            .expectBody<String>()
            .isEqualTo("open")

        restTestClient.get()
            .uri("/secure")
            .header("X-API-Version", "v1")
            .exchange()
            .expectStatus().isUnauthorized
    }

    @Test
    fun `버전 API는 버전 헤더가 없으면 요청을 거부한다`() {
        rawRestTestClient.get()
            .uri("/open")
            .exchange()
            .expectStatus().isBadRequest
    }

    @SpringBootConfiguration
    @EnableAutoConfiguration
    class TestApplication {
        @RestController
        @RequestMapping(version = "v1")
        class TestController {
            @GetMapping("/open")
            fun open(): String = "open"

            @GetMapping("/secure")
            fun secure(authentication: Authentication): String = authentication.name

            @GetMapping("/internal/secure")
            fun internalSecure(authentication: Authentication): String = authentication.name
        }
    }

    @TestConfiguration
    class RestTestClientConfiguration {
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
        fun rawRestTestClient(context: WebApplicationContext): RestTestClient {
            val mockMvc =
                MockMvcBuilders.webAppContextSetup(context)
                    .apply<DefaultMockMvcBuilder>(SecurityMockMvcConfigurers.springSecurity())
                    .build()
            return RestTestClient.bindTo(mockMvc).build()
        }

        @Bean
        fun accessTokenBlacklist(): AccessTokenBlacklist = FakeAccessTokenBlacklist()

        @Bean
        fun applicationOpenEndpointPolicy(): ApplicationOpenEndpointPolicy = StaticApplicationOpenEndpointPolicy(
            additionalMatchers =
            listOf(
                OpenEndpointMatcher(pattern = "/open"),
            ),
        )

        @Bean
        fun openEndpointCustomizer(policy: ApplicationOpenEndpointPolicy): AuthorizeHttpRequestsCustomizer = PrioritizedAuthorizeHttpRequestsCustomizer(
            0,
            ApplicationOpenEndpointsAuthorizeHttpRequestsCustomizer(policy),
        )
    }

    private class FakeAccessTokenBlacklist : AccessTokenBlacklist {
        private val tokens = mutableSetOf<String>()

        override fun blacklist(
            token: String,
            ttl: Duration,
        ) {
            tokens += token
        }

        override fun contains(token: String): Boolean = tokens.contains(token)
    }
}
