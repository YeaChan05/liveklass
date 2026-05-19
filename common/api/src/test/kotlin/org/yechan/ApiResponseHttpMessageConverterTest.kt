package org.yechan

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.core.io.ByteArrayResource
import org.springframework.http.MediaType
import org.springframework.http.server.ServletServerHttpResponse
import org.springframework.mock.web.MockHttpServletResponse
import tools.jackson.databind.JsonNode
import tools.jackson.databind.json.JsonMapper
import java.time.Clock
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneOffset

class ApiResponseHttpMessageConverterTest {
    private val clock: Clock =
        Clock.fixed(
            Instant.parse("2026-05-12T01:23:45Z"),
            ZoneOffset.UTC,
        )

    private val objectMapper: JsonMapper =
        JsonMapper.builder()
            .findAndAddModules()
            .build()

    private val converter =
        ApiResponseHttpMessageConverter(objectMapper, clock)

    @Test
    fun `성공 응답은 success true와 body와 timestamp로 감싼다`() {
        val servletResponse = MockHttpServletResponse()
        servletResponse.status = 200

        val outputMessage = ServletServerHttpResponse(servletResponse)

        converter.write(
            TestResponse(
                id = 1L,
                name = "course",
            ),
            MediaType.APPLICATION_JSON,
            outputMessage,
        )

        val json = readTree(servletResponse.contentAsString)

        assertThat(json["success"].asBoolean()).isTrue()
        assertThat(json["body"]["id"].asLong()).isEqualTo(1L)
        assertThat(json["body"]["name"].asString()).isEqualTo("course")
        assertThat(json["timestamp"].asString()).isEqualTo("2026-05-12T01:23:45")
        assertThat(json.has("message")).isFalse()
    }

    @Test
    fun `에러 응답은 success false와 message와 timestamp로 감싼다`() {
        val servletResponse = MockHttpServletResponse()
        servletResponse.status = 400

        val outputMessage = ServletServerHttpResponse(servletResponse)

        converter.write(
            "잘못된 요청입니다.",
            MediaType.APPLICATION_JSON,
            outputMessage,
        )

        val json = readTree(servletResponse.contentAsString)

        assertThat(json["success"].asBoolean()).isFalse()
        assertThat(json["message"].asString()).isEqualTo("잘못된 요청입니다.")
        assertThat(json["timestamp"].asString()).isEqualTo("2026-05-12T01:23:45")
        assertThat(json.has("body")).isFalse()
    }

    @Test
    fun `이미 ApiResponse 타입이면 다시 감싸지 않는다`() {
        val servletResponse = MockHttpServletResponse()
        servletResponse.status = 200

        val outputMessage = ServletServerHttpResponse(servletResponse)

        converter.write(
            ApiSuccessResponse(
                true,
                LocalDateTime.of(2026, 5, 12, 10, 0),
                TestResponse(
                    id = 10L,
                    name = "already-wrapped",
                ),
            ),
            MediaType.APPLICATION_JSON,
            outputMessage,
        )

        val json = readTree(servletResponse.contentAsString)

        assertThat(json["success"].asBoolean()).isTrue()
        assertThat(json["body"]["id"].asLong()).isEqualTo(10L)
        assertThat(json["body"]["name"].asString()).isEqualTo("already-wrapped")
        assertThat(json["timestamp"].asString()).isEqualTo("2026-05-12T10:00:00")
        assertThat(json["body"].has("success")).isFalse()
    }

    @Test
    fun `application json 계열 media type에 write 가능하다`() {
        assertThat(
            converter.canWrite(
                TestResponse::class.java,
                MediaType.APPLICATION_JSON,
            ),
        ).isTrue()

        assertThat(
            converter.canWrite(
                TestResponse::class.java,
                MediaType("application", "problem+json"),
            ),
        ).isTrue()
    }

    @Test
    fun `Resource 응답은 converter 대상에서 제외한다`() {
        assertThat(
            converter.canWrite(
                ByteArrayResource::class.java,
                MediaType.APPLICATION_JSON,
            ),
        ).isFalse()
    }

    private fun readTree(json: String): JsonNode = objectMapper.readTree(json)

    private data class TestResponse(
        val id: Long,
        val name: String,
    )
}
