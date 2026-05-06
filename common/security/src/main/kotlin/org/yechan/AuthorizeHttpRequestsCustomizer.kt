package org.yechan

import org.springframework.core.Ordered
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.annotation.web.configurers.AuthorizeHttpRequestsConfigurer

fun interface AuthorizeHttpRequestsCustomizer {
    fun customize(
        registry: AuthorizeHttpRequestsConfigurer<HttpSecurity>.AuthorizationManagerRequestMatcherRegistry,
    )
}

class PrioritizedAuthorizeHttpRequestsCustomizer(
    private val order: Int,
    private val delegate: AuthorizeHttpRequestsCustomizer,
) : AuthorizeHttpRequestsCustomizer,
    Ordered {
    override fun customize(
        registry: AuthorizeHttpRequestsConfigurer<HttpSecurity>.AuthorizationManagerRequestMatcherRegistry,
    ) {
        delegate.customize(registry)
    }

    override fun getOrder(): Int = order
}
