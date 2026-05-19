package org.yechan;

import org.springframework.core.Ordered;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AuthorizeHttpRequestsConfigurer;

public class PrioritizedAuthorizeHttpRequestsCustomizer implements AuthorizeHttpRequestsCustomizer, Ordered {
    private final int order;
    private final AuthorizeHttpRequestsCustomizer delegate;

    public PrioritizedAuthorizeHttpRequestsCustomizer(int order, AuthorizeHttpRequestsCustomizer delegate) {
        this.order = order;
        this.delegate = delegate;
    }

    @Override
    public void customize(
        AuthorizeHttpRequestsConfigurer<HttpSecurity>.AuthorizationManagerRequestMatcherRegistry registry
    ) {
        delegate.customize(registry);
    }

    @Override
    public int getOrder() {
        return order;
    }
}
