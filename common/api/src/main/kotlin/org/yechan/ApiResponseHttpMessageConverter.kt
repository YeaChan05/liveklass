package org.yechan

import org.springframework.http.HttpInputMessage
import org.springframework.http.HttpOutputMessage
import org.springframework.http.MediaType
import org.springframework.http.converter.AbstractHttpMessageConverter
import org.springframework.http.server.ServletServerHttpResponse
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody
import tools.jackson.databind.ObjectMapper
import java.io.ByteArrayOutputStream
import java.time.Clock
import java.time.LocalDateTime

class ApiResponseHttpMessageConverter(
    private val objectMapper: ObjectMapper,
    private val clock: Clock,
) : AbstractHttpMessageConverter<Any>(
    MediaType.APPLICATION_JSON,
    MediaType("application", "*+json"),
) {
    override fun supports(clazz: Class<*>): Boolean = !isExcludedType(clazz)

    override fun canWrite(
        clazz: Class<*>,
        mediaType: MediaType?,
    ): Boolean {
        if (isExcludedType(clazz)) {
            return false
        }

        return mediaType == null ||
            mediaType == MediaType.ALL ||
            mediaType.isCompatibleWith(MediaType.APPLICATION_JSON) ||
            mediaType.subtype.endsWith("+json")
    }

    override fun readInternal(
        clazz: Class<out Any>,
        inputMessage: HttpInputMessage,
    ): Any = throw UnsupportedOperationException("응답 쓰기 전용 MessageConverter입니다.")

    override fun writeInternal(
        body: Any,
        outputMessage: HttpOutputMessage,
    ) {
        outputMessage.headers.contentType = MediaType.APPLICATION_JSON

        val statusCode = resolveStatusCode(outputMessage)
        val response = wrap(body, statusCode)

        objectMapper.writeValue(outputMessage.body, response)
    }

    private fun wrap(
        body: Any,
        statusCode: Int,
    ): ApiResponse {
        if (body is ApiResponse) {
            return body
        }

        val now = LocalDateTime.now(clock)

        return if (statusCode >= 400) {
            ApiErrorResponse(
                message = extractMessage(body),
                timestamp = now,
            )
        } else {
            ApiSuccessResponse(
                body = body,
                timestamp = now,
            )
        }
    }

    private fun extractMessage(body: Any): String = when (body) {
        is String -> body
        is CharSequence -> body.toString()
        is Throwable -> body.message ?: body::class.simpleName ?: "Unknown error"
        else -> body.toString()
    }

    private fun resolveStatusCode(outputMessage: HttpOutputMessage): Int = when (outputMessage) {
        is ServletServerHttpResponse -> outputMessage.servletResponse.status
        else -> 200
    }

    private fun isExcludedType(clazz: Class<*>): Boolean = ByteArray::class.java.isAssignableFrom(clazz) ||
        ByteArrayOutputStream::class.java.isAssignableFrom(clazz) ||
        StreamingResponseBody::class.java.isAssignableFrom(clazz) ||
        org.springframework.core.io.Resource::class.java.isAssignableFrom(clazz)
}
