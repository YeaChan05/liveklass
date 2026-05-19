package org.yechan;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.Clock;
import java.time.LocalDateTime;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpInputMessage;
import org.springframework.http.HttpOutputMessage;
import org.springframework.http.MediaType;
import org.springframework.http.converter.AbstractHttpMessageConverter;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.http.converter.HttpMessageNotWritableException;
import org.springframework.http.server.ServletServerHttpResponse;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;
import tools.jackson.databind.ObjectMapper;

@NullMarked
class ApiResponseHttpMessageConverter extends AbstractHttpMessageConverter<Object> {

  private final ObjectMapper objectMapper;
  private final Clock clock;

  ApiResponseHttpMessageConverter(ObjectMapper objectMapper, Clock clock) {
    super(MediaType.APPLICATION_JSON, new MediaType("application", "*+json"));
    this.objectMapper = objectMapper;
    this.clock = clock;
  }

  @Override
  protected boolean supports(Class<?> clazz) {
    return !isExcludedType(clazz);
  }

  @Override
  public boolean canWrite(Class<?> clazz, @Nullable MediaType mediaType) {
    if (isExcludedType(clazz)) {
      return false;
    }

    return mediaType == null ||
        MediaType.ALL.equals(mediaType) || MediaType.APPLICATION_JSON.equals(mediaType)
        || mediaType.getSubtype().endsWith("+json");
  }

  @Override
  protected Object readInternal(Class<?> clazz, HttpInputMessage inputMessage)
      throws HttpMessageNotReadableException {
    throw new UnsupportedOperationException("응답 쓰기 전용 MessageConverter입니다.");
  }

  @Override
  protected void writeInternal(Object body, HttpOutputMessage outputMessage)
      throws IOException, HttpMessageNotWritableException {
    outputMessage.getHeaders().setContentType(MediaType.APPLICATION_JSON);

    int statusCode = resolveStatusCode(outputMessage);
    ApiResponse response = wrap(body, statusCode);

    objectMapper.writeValue(outputMessage.getBody(), response);
  }

  private ApiResponse wrap(Object body, int statusCode) {
    if (body instanceof ApiResponse response) {
      return response;
    }

    LocalDateTime now = LocalDateTime.now(clock);

    if (statusCode >= 400) {
      return new ApiErrorResponse(false, now, extractMessage(body));
    }
    return new ApiSuccessResponse<>(true, now, body);
  }

  private String extractMessage(Object body) {
    if (body instanceof String value) {
      return value;
    }
    if (body instanceof CharSequence value) {
      return value.toString();
    }
    if (body instanceof Throwable throwable) {
      if (throwable.getMessage() != null) {
        return throwable.getMessage();
      }
      return throwable.getClass().getSimpleName();
    }
    return String.valueOf(body);
  }

  private int resolveStatusCode(HttpOutputMessage outputMessage) {
    if (outputMessage instanceof ServletServerHttpResponse response) {
      return response.getServletResponse().getStatus();
    }
    return 200;
  }

  private boolean isExcludedType(Class<?> clazz) {
    return byte[].class.isAssignableFrom(clazz) ||
        ByteArrayOutputStream.class.isAssignableFrom(clazz) ||
        StreamingResponseBody.class.isAssignableFrom(clazz) ||
        Resource.class.isAssignableFrom(clazz);
  }
}
