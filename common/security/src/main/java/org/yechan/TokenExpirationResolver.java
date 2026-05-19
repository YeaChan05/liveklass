package org.yechan;

import java.time.Duration;

@FunctionalInterface
public interface TokenExpirationResolver {
    Duration remainingTime(String token);
}
