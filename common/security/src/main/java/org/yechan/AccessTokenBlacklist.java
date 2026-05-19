package org.yechan;

import java.time.Duration;

public interface AccessTokenBlacklist {
    void blacklist(String token, Duration ttl);

    boolean contains(String token);
}

final class NoOpAccessTokenBlacklist implements AccessTokenBlacklist {
    static final NoOpAccessTokenBlacklist INSTANCE = new NoOpAccessTokenBlacklist();

    private NoOpAccessTokenBlacklist() {
    }

    @Override
    public void blacklist(String token, Duration ttl) {
    }

    @Override
    public boolean contains(String token) {
        return false;
    }
}
