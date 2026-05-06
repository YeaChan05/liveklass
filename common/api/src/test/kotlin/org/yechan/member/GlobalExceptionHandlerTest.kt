package org.yechan.member

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus
import org.yechan.BusinessException
import org.yechan.GlobalExceptionHandler

class GlobalExceptionHandlerTest {
    @Test
    fun `비즈니스 예외 처리는 내부 서버 오류와 메시지 본문을 반환한다`() {
        val handler = GlobalExceptionHandler()
        val exception = BusinessException("message")

        val response = handler.handleBusinessException(exception)

        assertThat(response.statusCode).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR)
        assertThat(response.body).isSameAs(exception.message)
    }

    @Test
    fun `비즈니스 예외 처리는 하위 클래스 예외도 처리한다`() {
        val handler = GlobalExceptionHandler()
        val exception = SomeBusinessException("message")

        val response = handler.handleBusinessException(exception)

        assertThat(response.statusCode).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR)
        assertThat(response.body).isSameAs(exception.message)
    }

    private class SomeBusinessException(
        message: String,
    ) : BusinessException(message)
}
