package org.yechan;

import java.util.Set;

@FunctionalInterface
public interface TokenGenerator {
    AuthTokenValue generate(Long memberId, Set<String> roles);
}
