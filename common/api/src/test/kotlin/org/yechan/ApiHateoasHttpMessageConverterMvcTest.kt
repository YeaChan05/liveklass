package org.yechan

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.SpringBootConfiguration
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Import
import org.springframework.hateoas.Link
import org.springframework.http.MediaType
import org.springframework.test.context.ContextConfiguration
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@WebMvcTest(controllers = [ApiHateoasHttpMessageConverterMvcTest.TestController::class])
@AutoConfigureMockMvc(addFilters = false)
@ContextConfiguration(
    classes = [
        ApiHateoasHttpMessageConverterMvcTest.TestApplication::class,
        ApiHateoasHttpMessageConverterMvcTest.TestController::class,
        ApiHateoasHttpMessageConverterMvcTest.TestConfiguration::class,
    ],
)
class ApiHateoasHttpMessageConverterMvcTest {
    @Autowired
    lateinit var mockMvc: MockMvc

    @Test
    fun `컨트롤러가 원본 DTO를 반환해도 공통 MessageConverter가 HATEOAS 링크를 추가한다`() {
        val result = mockMvc.get("/hateoas-test/single") {
            accept = MediaType.APPLICATION_JSON
            header(HeaderConst.API_VERSION_HEADER, "v1")
        }.andExpect {
            status { isOk() }
            jsonPath("$.id") { value(1) }
            jsonPath("$.name") { value("course") }
            jsonPath("$._links.self.href") { exists() }
        }.andReturn()

        assertThat(result.response.contentAsString).doesNotContain("success")
    }

    @Test
    fun `목록 응답은 각 원소에 HATEOAS 링크를 추가한다`() {
        mockMvc.get("/hateoas-test/list") {
            accept = MediaType.APPLICATION_JSON
            header(HeaderConst.API_VERSION_HEADER, "v1")
        }.andExpect {
            status { isOk() }
            jsonPath("$[0].id") { value(1) }
            jsonPath("$[0]._links.self.href") { exists() }
            jsonPath("$[1].id") { value(2) }
            jsonPath("$[1]._links.self.href") { exists() }
        }
    }

    @SpringBootConfiguration
    class TestApplication

    @RestController
    @RequestMapping("/hateoas-test", version = "v1")
    class TestController {
        @GetMapping("/single")
        fun single(): TestResponse = TestResponse(1L, "course")

        @GetMapping("/list")
        fun list(): List<TestResponse> = listOf(
            TestResponse(1L, "course"),
            TestResponse(2L, "spring"),
        )
    }

    @Configuration(proxyBeanMethods = false)
    @Import(CommonApiBeanRegistrar::class)
    class TestConfiguration {
        @Bean
        fun testLinkProvider(): ApiHateoasLinkProvider = object : ApiHateoasLinkProvider {
            override fun supports(body: Any): Boolean = body is TestResponse

            override fun links(body: Any): Iterable<Link> {
                val response = body as TestResponse
                return listOf(Link.of("/hateoas-test/${response.id}").withSelfRel())
            }
        }
    }

    data class TestResponse(
        val id: Long,
        val name: String,
    )
}
