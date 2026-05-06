package org.yechan

import org.junit.jupiter.api.Test
import org.mockito.Mockito
import org.springframework.http.HttpMethod

class ApplicationOpenEndpointsAuthorizeHttpRequestsCustomizerTest {

    @Test
    fun `additional matchers는 method 유무에 맞춰 공개 경로를 추가한다`() {
        val registry = mockApplicationRequestMatcherRegistry()

        ApplicationOpenEndpointsAuthorizeHttpRequestsCustomizer(
            StaticApplicationOpenEndpointPolicy(
                additionalMatchers =
                listOf(
                    OpenEndpointMatcher(HttpMethod.POST, "/login"),
                    OpenEndpointMatcher(pattern = "/internal/open"),
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
