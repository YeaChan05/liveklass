package org.yechan

import org.springframework.beans.factory.BeanRegistrarDsl
import org.springframework.boot.autoconfigure.AutoConfiguration
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder
import org.springframework.security.crypto.password.PasswordEncoder

@AutoConfiguration
class PasswordEncoderBeanRegistrar :
    BeanRegistrarDsl({
        registerBean<PasswordEncoder>(fallback = true) {
            BCryptPasswordEncoder()
        }

        registerBean<PasswordHashEncoder>(fallback = true) {
            BcryptPasswordHashEncoder(bean())
        }
    })
