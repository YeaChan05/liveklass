package org.yechan;

import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AuthorizeHttpRequestsConfigurer;

public class ApplicationOpenEndpointsAuthorizeHttpRequestsCustomizer implements AuthorizeHttpRequestsCustomizer {
    private final ApplicationOpenEndpointPolicy policy;

    public ApplicationOpenEndpointsAuthorizeHttpRequestsCustomizer(ApplicationOpenEndpointPolicy policy) {
        this.policy = policy;
    }

    @Override
    public void customize(
        AuthorizeHttpRequestsConfigurer<HttpSecurity>.AuthorizationManagerRequestMatcherRegistry registry
    ) {
        for (OpenEndpointMatcher matcher : policy.additionalMatchers()) {
            if (matcher.method() == null) {
                registry.requestMatchers(matcher.pattern()).permitAll();
            } else {
                registry.requestMatchers(matcher.method(), matcher.pattern()).permitAll();
            }
        }
    }
}
