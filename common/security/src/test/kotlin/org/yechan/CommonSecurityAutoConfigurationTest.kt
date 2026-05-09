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
import org.springframework.context.annotation.Import
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
import org.yechan.auth.CourseJwtAutoConfiguration
import org.yechan.auth.CourseJwtBeanRegistrar
import org.yechan.auth.MemberSecurityAdapterBeanRegistrar
import org.yechan.auth.MemberSecurityAdapterConfiguration
import org.yechan.course.CourseAuthorizationPolicy
import org.yechan.course.CourseOpenEndpointPolicy
import org.yechan.course.CourseRoleHierarchyConfiguration
import org.yechan.member.MemberAuthOpenEndpointPolicy
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
    fun `토큰 생성기는 역할 없는 생성 메서드를 노출하지 않는다`() {
        val generateMethods = TokenGenerator::class.java.methods
            .filter { it.name == "generate" }

        Assertions.assertThat(generateMethods)
            .allSatisfy {
                Assertions.assertThat(it.parameterCount).isEqualTo(2)
            }
    }

    @Test
    fun `토큰이 없는 요청은 인증 실패로 거부된다`() {
        restTestClient.get()
            .uri("/secure")
            .header(HeaderConst.API_VERSION_HEADER, "v1")
            .exchange()
            .expectStatus().isUnauthorized
    }

    @Test
    fun `잘못된 토큰 요청은 인증 실패로 거부된다`() {
        restTestClient.get()
            .uri("/secure")
            .header(HeaderConst.API_VERSION_HEADER, "v1")
            .header(HttpHeaders.AUTHORIZATION, "Bearer invalid")
            .exchange()
            .expectStatus().isUnauthorized
    }

    @Test
    fun `올바른 토큰 요청은 허용된다`() {
        val token = tokenGenerator.generate(1L, roles = emptySet()).accessToken

        restTestClient.get()
            .uri("/secure")
            .header(HeaderConst.API_VERSION_HEADER, "v1")
            .header(HttpHeaders.AUTHORIZATION, "Bearer $token")
            .exchange()
            .expectStatus().isOk
            .expectBody<String>()
            .isEqualTo("1")
    }

    @Test
    fun `토큰 역할 클레임은 스프링 시큐리티 authority로 복원된다`() {
        val token = tokenGenerator.generate(1L, roles = setOf("ADMIN")).accessToken

        restTestClient.get()
            .uri("/authority")
            .header(HeaderConst.API_VERSION_HEADER, "v1")
            .header(HttpHeaders.AUTHORIZATION, "Bearer $token")
            .exchange()
            .expectStatus().isOk
            .expectBody<String>()
            .isEqualTo("ROLE_ADMIN")
    }

    @Test
    fun `모듈이 주입한 역할 정책은 토큰 authority로 판정된다`() {
        val adminToken = tokenGenerator.generate(1L, roles = setOf("ADMIN")).accessToken
        val userToken = tokenGenerator.generate(1L, roles = setOf("USER")).accessToken

        restTestClient.get()
            .uri("/admin")
            .header(HeaderConst.API_VERSION_HEADER, "v1")
            .header(HttpHeaders.AUTHORIZATION, "Bearer $adminToken")
            .exchange()
            .expectStatus().isOk

        restTestClient.get()
            .uri("/admin")
            .header(HeaderConst.API_VERSION_HEADER, "v1")
            .header(HttpHeaders.AUTHORIZATION, "Bearer $userToken")
            .exchange()
            .expectStatus().isForbidden
    }

    @Test
    fun `블랙리스트에 등록된 토큰 요청은 인증 실패로 거부된다`() {
        val token = tokenGenerator.generate(1L, roles = emptySet()).accessToken
        context.getBean(AccessTokenBlacklist::class.java).blacklist(token, Duration.ofMinutes(10))

        restTestClient.get()
            .uri("/secure")
            .header(HeaderConst.API_VERSION_HEADER, "v1")
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
            .header(HeaderConst.API_VERSION_HEADER, "v1")
            .exchange()
            .expectStatus().isOk
            .expectBody<String>()
            .isEqualTo("open")

        restTestClient.get()
            .uri("/secure")
            .header(HeaderConst.API_VERSION_HEADER, "v1")
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
    @EnableAutoConfiguration(
        exclude = [
            CourseJwtAutoConfiguration::class,
            CourseJwtBeanRegistrar::class,
            MemberSecurityAdapterConfiguration::class,
            MemberSecurityAdapterBeanRegistrar::class,
            MemberAuthOpenEndpointPolicy::class,
            CourseOpenEndpointPolicy::class,
            CourseAuthorizationPolicy::class,
            CourseRoleHierarchyConfiguration::class,
        ],
        excludeName = [
            "org.yechan.ServiceAutoConfiguration",
            "org.yechan.ServiceBeanRegistrar",
        ],

    )
    @Import(
        CourseJwtAutoConfiguration::class,
        CourseJwtBeanRegistrar::class,
    )
    class TestApplication {
        @RestController
        @RequestMapping(version = "v1")
        class TestController {
            @GetMapping("/open")
            fun open(): String = "open"

            @GetMapping("/secure")
            fun secure(authentication: Authentication): String = authentication.name

            @GetMapping("/authority")
            fun authority(authentication: Authentication): String = authentication.authorities
                .mapNotNull { it.authority }
                .joinToString(",")

            @GetMapping("/admin")
            fun admin(): String = "admin"

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
                .apiVersionInserter(ApiVersionInserter.useHeader(HeaderConst.API_VERSION_HEADER))
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
        @org.springframework.context.annotation.Primary
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

        @Bean
        fun adminEndpointCustomizer(): AuthorizeHttpRequestsCustomizer = PrioritizedAuthorizeHttpRequestsCustomizer(
            1,
        ) { registry ->
            registry.requestMatchers("/admin").hasRole("ADMIN")
        }
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
