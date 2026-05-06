package org.yechan

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class StatusTest {
    @Test
    fun `상태는 HTTP 상태 코드로 변환된다`() {
        assertThat(Status.BAD_REQUEST.toHttpStatus()).isEqualTo(400)
        assertThat(Status.CONFLICT.toHttpStatus()).isEqualTo(409)
        assertThat(Status.RESOURCE_NOT_FOUND.toHttpStatus()).isEqualTo(404)
        assertThat(Status.INTERNAL_SERVER_ERROR.toHttpStatus()).isEqualTo(500)
        assertThat(Status.AUTHENTICATION_FAILED.toHttpStatus()).isEqualTo(401)
        assertThat(Status.ACCESS_DENIED.toHttpStatus()).isEqualTo(403)
        assertThat(Status.TOKEN_EXPIRED.toHttpStatus()).isEqualTo(401)
    }
}
