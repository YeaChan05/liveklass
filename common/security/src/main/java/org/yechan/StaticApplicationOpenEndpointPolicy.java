package org.yechan;

import java.util.List;

public record StaticApplicationOpenEndpointPolicy(
    boolean includeHealth,
    List<OpenEndpointMatcher> additionalMatchers
) implements ApplicationOpenEndpointPolicy {
}
