package org.yechan.swagger

import org.hamcrest.Matchers.containsString
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.SpringBootConfiguration
import org.springframework.boot.autoconfigure.EnableAutoConfiguration
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Import
import org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.content
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.test.web.servlet.setup.DefaultMockMvcBuilder
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.context.WebApplicationContext
import org.yechan.ServiceAutoConfiguration
import org.yechan.SwaggerOpenEndpointPolicy
import org.yechan.auth.JwtAutoConfiguration
import org.yechan.member.MemberSecurityAdapterConfiguration

@SpringBootTest(
    classes = [
        SwaggerOpenEndpointPolicyTest.TestApplication::class,
        SwaggerOpenEndpointPolicyTest.TestBeans::class,
    ],
)
class SwaggerOpenEndpointPolicyTest {
    @Autowired
    lateinit var mockMvc: MockMvc

    @Test
    fun `OpenAPI JSON 문서는 인증 없이 접근할 수 있다`() {
        mockMvc.perform(get("/v3/api-docs"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.openapi").exists())
    }

    @Test
    fun `Swagger UI는 인증 없이 접근할 수 있다`() {
        mockMvc.perform(get("/swagger-ui/index.html"))
            .andExpect(status().isOk)
            .andExpect(content().string(containsString("Swagger UI")))
    }

    @Test
    fun `Swagger 공개 정책은 기본 인증 정책을 넓히지 않는다`() {
        mockMvc.perform(get("/secure"))
            .andExpect(status().isUnauthorized)
    }

    @SpringBootConfiguration
    @EnableAutoConfiguration(
        exclude = [
            JwtAutoConfiguration::class,
            ServiceAutoConfiguration::class,
            MemberSecurityAdapterConfiguration::class,
        ],
    )
    @Import(SwaggerOpenEndpointPolicy::class)
    class TestApplication {
        @RestController
        class SecureController {
            @GetMapping("/secure")
            fun secure(): String = "secure"
        }
    }

    @TestConfiguration
    class TestBeans {
        @Bean
        fun mockMvc(context: WebApplicationContext): MockMvc = MockMvcBuilders.webAppContextSetup(context)
            .apply<DefaultMockMvcBuilder>(SecurityMockMvcConfigurers.springSecurity())
            .build()
    }
}
