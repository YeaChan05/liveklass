package org.yechan;

import org.springframework.security.config.annotation.web.builders.HttpSecurity;

@FunctionalInterface
public interface SecurityFilterChainCustomizer {
    void customize(HttpSecurity http) throws Exception;
}
