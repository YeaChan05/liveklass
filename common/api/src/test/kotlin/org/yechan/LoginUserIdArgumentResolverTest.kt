package org.yechan

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.springframework.core.MethodParameter
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.security.authentication.AnonymousAuthenticationToken
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.web.context.request.ServletWebRequest
import org.springframework.web.method.support.ModelAndViewContainer

class LoginUserIdArgumentResolverTest {
    private val resolver = LoginUserIdArgumentResolver()
    private val webRequest = ServletWebRequest(MockHttpServletRequest())

    @AfterEach
    fun clearContext() {
        SecurityContextHolder.clearContext()
    }

    @Test
    fun `어노테이션이 붙은 Long 파라미터를 지원한다`() {
        val parameter = MethodParameter(method("handlerWithLong", Long::class.javaObjectType), 0)

        assertThat(resolver.supportsParameter(parameter)).isTrue()
    }

    @Test
    fun `어노테이션이 붙은 primitive long 파라미터를 지원한다`() {
        val parameter =
            MethodParameter(method("handlerWithPrimitive", Long::class.javaPrimitiveType!!), 0)

        assertThat(resolver.supportsParameter(parameter)).isTrue()
    }

    @Test
    fun `어노테이션이 없는 파라미터는 지원하지 않는다`() {
        val parameter =
            MethodParameter(method("handlerWithoutAnnotation", Long::class.javaObjectType), 0)

        assertThat(resolver.supportsParameter(parameter)).isFalse()
    }

    @Test
    fun `인증된 요청은 현재 사용자 아이디를 해석한다`() {
        SecurityContextHolder.getContext().authentication =
            UsernamePasswordAuthenticationToken(
                "42",
                "credentials",
                listOf(SimpleGrantedAuthority("ROLE_USER")),
            )
        val parameter = MethodParameter(method("handlerWithLong", Long::class.javaObjectType), 0)

        val result = resolver.resolveArgument(parameter, ModelAndViewContainer(), webRequest, null)

        assertThat(result).isEqualTo(42L)
    }

    @Test
    fun `인증 정보가 없으면 unauthorized를 던진다`() {
        val parameter = MethodParameter(method("handlerWithLong", Long::class.javaObjectType), 0)

        assertThatThrownBy {
            resolver.resolveArgument(parameter, ModelAndViewContainer(), webRequest, null)
        }
            .isInstanceOf(BusinessException::class.java)
            .hasMessage("Unauthorized")
    }

    @Test
    fun `anonymous 인증은 unauthorized를 던진다`() {
        SecurityContextHolder.getContext().authentication =
            AnonymousAuthenticationToken(
                "key",
                "anonymous",
                listOf(SimpleGrantedAuthority("ROLE_ANONYMOUS")),
            )
        val parameter = MethodParameter(method("handlerWithLong", Long::class.javaObjectType), 0)

        assertThatThrownBy {
            resolver.resolveArgument(parameter, ModelAndViewContainer(), webRequest, null)
        }
            .isInstanceOf(BusinessException::class.java)
            .hasMessage("Unauthorized")
    }

    @Test
    fun `숫자가 아닌 사용자 아이디는 bad request를 던진다`() {
        SecurityContextHolder.getContext().authentication =
            UsernamePasswordAuthenticationToken(
                "not-a-number",
                "credentials",
                listOf(SimpleGrantedAuthority("ROLE_USER")),
            )
        val parameter = MethodParameter(method("handlerWithLong", Long::class.javaObjectType), 0)

        assertThatThrownBy {
            resolver.resolveArgument(parameter, ModelAndViewContainer(), webRequest, null)
        }
            .isInstanceOf(BusinessException::class.java)
            .hasMessage("Invalid user id")
    }

    private fun method(
        name: String,
        parameterType: Class<*>,
    ) = TestController::class.java.getDeclaredMethod(name, parameterType)

    private class TestController {
        fun handlerWithLong(
            @LoginUserId userId: Long?,
        ) {
        }

        fun handlerWithPrimitive(
            @LoginUserId userId: Long,
        ) {
        }

        fun handlerWithoutAnnotation(userId: Long?) {
        }
    }
}
