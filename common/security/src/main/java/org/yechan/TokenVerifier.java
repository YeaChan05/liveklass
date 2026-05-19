package org.yechan;

import org.springframework.security.core.Authentication;

@FunctionalInterface
public interface TokenVerifier {
    Authentication verify(String token);
}
