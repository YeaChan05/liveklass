package org.yechan;

import org.springframework.http.HttpMethod;

public record OpenEndpointMatcher(
    HttpMethod method,
    String pattern
) {
}
