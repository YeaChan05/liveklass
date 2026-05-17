package org.yechan

import org.springframework.core.io.Resource
import org.springframework.hateoas.Link
import org.springframework.hateoas.RepresentationModel
import org.springframework.http.HttpInputMessage
import org.springframework.http.HttpOutputMessage
import org.springframework.http.MediaType
import org.springframework.http.converter.AbstractHttpMessageConverter
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody
import tools.jackson.databind.ObjectMapper
import java.beans.Introspector
import java.io.ByteArrayOutputStream

class ApiHateoasHttpMessageConverter(
    private val objectMapper: ObjectMapper,
    private val linkProviders: List<ApiHateoasLinkProvider>,
) : AbstractHttpMessageConverter<Any>(
    MediaType.APPLICATION_JSON,
    MediaType("application", "*+json"),
) {
    override fun supports(clazz: Class<*>): Boolean = linkProviders.isNotEmpty() && !clazz.isExcludedType()

    override fun canWrite(
        clazz: Class<*>,
        mediaType: MediaType?,
    ): Boolean {
        if (!supports(clazz)) {
            return false
        }

        return when (mediaType) {
            null, MediaType.ALL, MediaType.APPLICATION_JSON -> true
            else -> mediaType.subtype.endsWith("+json")
        }
    }

    override fun canRead(
        clazz: Class<*>,
        mediaType: MediaType?,
    ): Boolean = false

    override fun readInternal(
        clazz: Class<out Any>,
        inputMessage: HttpInputMessage,
    ): Any = throw UnsupportedOperationException("응답 쓰기 전용 MessageConverter입니다.")

    override fun writeInternal(
        body: Any,
        outputMessage: HttpOutputMessage,
    ) {
        outputMessage.headers.contentType = MediaType.APPLICATION_JSON
        objectMapper.writeValue(outputMessage.body, body.withHateoasLinks())
    }

    private fun Any.withHateoasLinks(): Any = when (this) {
        is RepresentationModel<*> -> this
        is Collection<*> -> map { item -> item?.withHateoasLinks() }
        is Array<*> -> map { item -> item?.withHateoasLinks() }
        else -> toHateoasModelIfLinkExists()
    }

    private fun Any.toHateoasModelIfLinkExists(): Any {
        val links = linkProviders
            .asSequence()
            .filter { provider -> provider.supports(this) }
            .flatMap { provider -> provider.links(this).asSequence() }
            .toList()

        return if (links.isEmpty()) {
            this
        } else {
            linkedMapOf<String, Any?>().apply {
                putAll(this@toHateoasModelIfLinkExists.toPropertyMap())
                put("_links", links.toLinks())
            }
        }
    }

    private fun List<Link>.toLinks(): Map<String, ApiHateoasLink> = associate { link ->
        link.rel.value() to ApiHateoasLink(href = link.href)
    }

    private fun Class<*>.isExcludedType(): Boolean = ApiResponse::class.java.isAssignableFrom(this) ||
        RepresentationModel::class.java.isAssignableFrom(this) ||
        CharSequence::class.java.isAssignableFrom(this) ||
        ByteArray::class.java.isAssignableFrom(this) ||
        ByteArrayOutputStream::class.java.isAssignableFrom(this) ||
        Resource::class.java.isAssignableFrom(this) ||
        SseEmitter::class.java.isAssignableFrom(this) ||
        StreamingResponseBody::class.java.isAssignableFrom(this)

    private fun Any.toPropertyMap(): Map<String, Any?> = Introspector
        .getBeanInfo(javaClass)
        .propertyDescriptors
        .asSequence()
        .filter { descriptor -> descriptor.name != "class" && descriptor.readMethod != null }
        .associate { descriptor -> descriptor.name to descriptor.readMethod.invoke(this) }
}

data class ApiHateoasLink(
    val href: String,
)
