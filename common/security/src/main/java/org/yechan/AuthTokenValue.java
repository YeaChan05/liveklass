package org.yechan;

public record AuthTokenValue(
    String accessToken,
    String refreshToken,
    long expiresIn
) {
}
