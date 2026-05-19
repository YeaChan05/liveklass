package org.yechan;

import org.springframework.security.crypto.password.PasswordEncoder;

public interface PasswordHashEncoder {
    String encode(String password);

    boolean matches(String password, String encodedPassword);
}

class BcryptPasswordHashEncoder implements PasswordHashEncoder {
    private final PasswordEncoder passwordEncoder;

    BcryptPasswordHashEncoder(PasswordEncoder passwordEncoder) {
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    public String encode(String password) {
        return passwordEncoder.encode(password);
    }

    @Override
    public boolean matches(String password, String encodedPassword) {
        return passwordEncoder.matches(password, encodedPassword);
    }
}
