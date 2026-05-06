package org.yechan

import org.springframework.security.crypto.password.PasswordEncoder

class BcryptPasswordHashEncoder(
    private val passwordEncoder: PasswordEncoder,
) : PasswordHashEncoder {
    override fun encode(password: String): String = passwordEncoder.encode(password)!!

    override fun matches(
        password: String,
        encodedPassword: String,
    ): Boolean = passwordEncoder.matches(password, encodedPassword)
}
