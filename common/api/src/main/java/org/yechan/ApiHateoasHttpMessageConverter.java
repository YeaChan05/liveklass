package org.yechan;

import java.beans.IntrospectionException;
import java.beans.Introspector;
import java.beans.PropertyDescriptor;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;
import org.springframework.core.io.Resource;
import org.springframework.hateoas.Link;
import org.springframework.hateoas.RepresentationModel;
import org.springframework.http.HttpInputMessage;
import org.springframework.http.HttpOutputMessage;
import org.springframework.http.MediaType;
import org.springframework.http.converter.AbstractHttpMessageConverter;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.http.converter.HttpMessageNotWritableException;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;
import tools.jackson.databind.ObjectMapper;

class ApiHateoasHttpMessageConverter extends AbstractHttpMessageConverter<Object> {
    private final ObjectMapper objectMapper;
    private final List<ApiHateoasLinkProvider> linkProviders;

    ApiHateoasHttpMessageConverter(ObjectMapper objectMapper, List<ApiHateoasLinkProvider> linkProviders) {
        super(MediaType.APPLICATION_JSON, new MediaType("application", "*+json"));
        this.objectMapper = objectMapper;
        this.linkProviders = linkProviders;
    }

    @Override
    protected boolean supports(Class<?> clazz) {
        return !linkProviders.isEmpty() && !isExcludedType(clazz);
    }

    @Override
    public boolean canWrite(Class<?> clazz, MediaType mediaType) {
        if (!supports(clazz)) {
            return false;
        }

        return mediaType == null ||
            MediaType.ALL.equals(mediaType) ||
            MediaType.APPLICATION_JSON.equals(mediaType) ||
            mediaType.getSubtype().endsWith("+json");
    }

    @Override
    public boolean canRead(Class<?> clazz, MediaType mediaType) {
        return false;
    }

    @Override
    protected Object readInternal(Class<? extends Object> clazz, HttpInputMessage inputMessage)
        throws HttpMessageNotReadableException {
        throw new UnsupportedOperationException("응답 쓰기 전용 MessageConverter입니다.");
    }

    @Override
    protected void writeInternal(Object body, HttpOutputMessage outputMessage)
        throws IOException, HttpMessageNotWritableException {
        outputMessage.getHeaders().setContentType(MediaType.APPLICATION_JSON);
        objectMapper.writeValue(outputMessage.getBody(), withHateoasLinks(body));
    }

    private Object withHateoasLinks(Object body) {
        if (body instanceof RepresentationModel<?>) {
            return body;
        }
        if (body instanceof Collection<?> collection) {
            return collection.stream()
                .map(item -> item == null ? null : withHateoasLinks(item))
                .toList();
        }
        if (body instanceof Object[] array) {
            return Arrays.stream(array)
                .map(item -> item == null ? null : withHateoasLinks(item))
                .toList();
        }
        return toHateoasModelIfLinkExists(body);
    }

    private Object toHateoasModelIfLinkExists(Object body) {
        List<Link> links = linkProviders.stream()
            .filter(provider -> provider.supports(body))
            .flatMap(provider -> StreamSupport.stream(provider.links(body).spliterator(), false))
            .toList();

        if (links.isEmpty()) {
            return body;
        }

        Map<String, Object> model = new LinkedHashMap<>(toPropertyMap(body));
        model.put("_links", toLinks(links));
        return model;
    }

    private Map<String, ApiHateoasLink> toLinks(List<Link> links) {
        return links.stream()
            .collect(Collectors.toMap(
                link -> link.getRel().value(),
                link -> new ApiHateoasLink(link.getHref()),
                (left, right) -> right,
                LinkedHashMap::new
            ));
    }

    private boolean isExcludedType(Class<?> clazz) {
        return ApiResponse.class.isAssignableFrom(clazz) ||
            RepresentationModel.class.isAssignableFrom(clazz) ||
            CharSequence.class.isAssignableFrom(clazz) ||
            byte[].class.isAssignableFrom(clazz) ||
            ByteArrayOutputStream.class.isAssignableFrom(clazz) ||
            Resource.class.isAssignableFrom(clazz) ||
            SseEmitter.class.isAssignableFrom(clazz) ||
            StreamingResponseBody.class.isAssignableFrom(clazz);
    }

    private Map<String, Object> toPropertyMap(Object body) {
        try {
            Map<String, Object> properties = new LinkedHashMap<>();
            for (PropertyDescriptor descriptor : Introspector.getBeanInfo(body.getClass()).getPropertyDescriptors()) {
                if (!"class".equals(descriptor.getName()) && descriptor.getReadMethod() != null) {
                    properties.put(descriptor.getName(), readProperty(descriptor, body));
                }
            }
            return properties;
        } catch (IntrospectionException e) {
            throw new IllegalStateException("HATEOAS 링크 생성을 위한 응답 속성 분석에 실패했습니다.", e);
        }
    }

    private Object readProperty(PropertyDescriptor descriptor, Object body) {
        try {
            return descriptor.getReadMethod().invoke(body);
        } catch (IllegalAccessException | InvocationTargetException e) {
            throw new IllegalStateException("HATEOAS 링크 생성을 위한 응답 속성 읽기에 실패했습니다.", e);
        }
    }
}

record ApiHateoasLink(String href) {
}
