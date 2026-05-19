package org.yechan

import org.junit.jupiter.api.Test
import org.mockito.Mockito
import org.springframework.http.HttpMethod
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.annotation.web.configurers.AuthorizeHttpRequestsConfigurer

class ApplicationOpenEndpointsAuthorizeHttpRequestsCustomizerTest {

    @Test
    fun `추가 경로 조건은 HTTP 메서드 유무에 맞춰 공개 경로를 추가한다`() {
        val registry = mockApplicationRequestMatcherRegistry()

        ApplicationOpenEndpointsAuthorizeHttpRequestsCustomizer(
            StaticApplicationOpenEndpointPolicy(
                false,
                listOf(
                    OpenEndpointMatcher(HttpMethod.POST, "/login"),
                    OpenEndpointMatcher(null, "/internal/open"),
                ),
            ),
        ).customize(registry)

        Mockito.verify(registry).requestMatchers(HttpMethod.POST, "/login")
        Mockito.verify(registry).requestMatchers("/internal/open")
    }

    private fun mockApplicationRequestMatcherRegistry(): ApplicationRequestMatcherRegistry {
        @Suppress("UNCHECKED_CAST")
        return Mockito.mock(
            ApplicationRequestMatcherRegistry::class.java,
            Mockito.RETURNS_DEEP_STUBS,
        ) as ApplicationRequestMatcherRegistry
    }
}

private typealias ApplicationRequestMatcherRegistry =
    AuthorizeHttpRequestsConfigurer<HttpSecurity>.AuthorizationManagerRequestMatcherRegistry
