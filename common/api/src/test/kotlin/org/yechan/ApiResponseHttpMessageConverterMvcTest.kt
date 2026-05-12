package org.yechan

import org.assertj.core.api.Assertions
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.SpringBootConfiguration
import org.springframework.boot.autoconfigure.EnableAutoConfiguration
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest
import org.springframework.context.annotation.Bean
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.http.converter.HttpMessageConverters
import org.springframework.test.context.ContextConfiguration
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.bind.annotation.RestControllerAdvice
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer
import tools.jackson.databind.json.JsonMapper
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

@WebMvcTest(controllers = [ApiResponseHttpMessageConverterMvcTest.MvcTestController::class])
@AutoConfigureMockMvc(addFilters = false)
@ContextConfiguration(
    classes = [
        ApiResponseHttpMessageConverterMvcTest.TestApplication::class,
        ApiResponseHttpMessageConverterMvcTest.MvcTestController::class,
        ApiResponseHttpMessageConverterMvcTest.MvcTestExceptionHandler::class,
        ApiResponseHttpMessageConverterMvcTest.MvcTestConfiguration::class,
    ],
)
class ApiResponseHttpMessageConverterMvcTest {
    @Autowired
    lateinit var mockMvc: MockMvc

    private val objectMapper: JsonMapper =
        JsonMapper.builder()
            .findAndAddModules()
            .build()

    @Test
    fun `컨트롤러 정상 응답은 실제 MVC 흐름에서 공통 응답으로 감싸진다`() {
        val result =
            mockMvc.get("/converter-test/success") {
                accept = MediaType.APPLICATION_JSON
            }
                .andExpect {
                    status { isOk() }
                    content { contentTypeCompatibleWith(MediaType.APPLICATION_JSON) }
                }
                .andReturn()

        val json = objectMapper.readTree(result.response.contentAsString)

        Assertions.assertThat(json["success"].asBoolean()).isTrue()
        Assertions.assertThat(json["body"]["id"].asLong()).isEqualTo(1L)
        Assertions.assertThat(json["body"]["name"].asString()).isEqualTo("course")
        Assertions.assertThat(json["timestamp"].asString()).isEqualTo("2026-05-12T01:23:45")
        Assertions.assertThat(json.has("message")).isFalse()
    }

    @Test
    fun `예외 핸들러 응답은 실제 MVC 흐름에서 에러 공통 응답으로 감싸진다`() {
        val result =
            mockMvc.get("/converter-test/error") {
                accept = MediaType.APPLICATION_JSON
            }
                .andExpect {
                    status { isBadRequest() }
                    content { contentTypeCompatibleWith(MediaType.APPLICATION_JSON) }
                }
                .andReturn()

        val json = objectMapper.readTree(result.response.contentAsString)

        Assertions.assertThat(json["success"].asBoolean()).isFalse()
        Assertions.assertThat(json["message"].asString()).isEqualTo("테스트 예외")
        Assertions.assertThat(json["timestamp"].asString()).isEqualTo("2026-05-12T01:23:45")
        Assertions.assertThat(json.has("body")).isFalse()
    }

    @SpringBootConfiguration
    @EnableAutoConfiguration
    class TestApplication

    @RestController
    class MvcTestController {
        @GetMapping("/converter-test/success")
        fun success(): TestResponse = TestResponse(
            id = 1L,
            name = "course",
        )

        @GetMapping("/converter-test/error")
        fun error(): TestResponse = throw MvcTestException("테스트 예외")
    }

    @RestControllerAdvice
    class MvcTestExceptionHandler {
        @ExceptionHandler(MvcTestException::class)
        fun handle(e: MvcTestException): ResponseEntity<Any> = ResponseEntity
            .badRequest()
            .body(e.message)
    }

    @TestConfiguration
    class MvcTestConfiguration {
        @Bean
        fun apiResponseHttpMessageConverterWebMvcConfigurer(): WebMvcConfigurer {
            val objectMapper =
                JsonMapper.builder()
                    .findAndAddModules()
                    .build()

            val clock =
                Clock.fixed(
                    Instant.parse("2026-05-12T01:23:45Z"),
                    ZoneOffset.UTC,
                )

            return object : WebMvcConfigurer {
                override fun configureMessageConverters(builder: HttpMessageConverters.ServerBuilder) {
                    builder.configureMessageConvertersList { converters ->
                        converters.add(
                            0,
                            ApiResponseHttpMessageConverter(
                                objectMapper = objectMapper,
                                clock = clock,
                            ),
                        )
                    }
                }
            }
        }
    }

    data class TestResponse(
        val id: Long,
        val name: String,
    )

    class MvcTestException(
        override val message: String,
    ) : RuntimeException(message)
}
