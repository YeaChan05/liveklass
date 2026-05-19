package org.yechan;

import java.util.List;

public interface ApplicationOpenEndpointPolicy {

  List<OpenEndpointMatcher> additionalMatchers();
}
