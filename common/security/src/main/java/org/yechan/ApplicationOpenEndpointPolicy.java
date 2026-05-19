package org.yechan;

import java.util.List;

public interface ApplicationOpenEndpointPolicy {
    boolean includeHealth();

    List<OpenEndpointMatcher> additionalMatchers();
}
