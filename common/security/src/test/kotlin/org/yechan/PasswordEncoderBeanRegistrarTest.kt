package org.yechan

import org.assertj.core.api.Assertions
import org.junit.jupiter.api.Test
import org.springframework.context.annotation.AnnotationConfigApplicationContext
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Import
import org.springframework.security.crypto.password.PasswordEncoder

class PasswordEncoderBeanRegistrarTest {
    @Test
    fun `자동 설정은 password encoder 관련 빈을 등록한다`() {
        val context = AnnotationConfigApplicationContext().apply {
            register(TestConfiguration::class.java)
            refresh()
        }

        val passwordEncoder = context.getBean(PasswordEncoder::class.java)
        val passwordHashEncoder = context.getBean(PasswordHashEncoder::class.java)
        val encoded = passwordHashEncoder.encode("Password!1")

        Assertions.assertThat(passwordEncoder.matches("Password!1", encoded)).isTrue()
        Assertions.assertThat(passwordHashEncoder.matches("Password!1", encoded)).isTrue()

        context.close()
    }

    @Configuration(proxyBeanMethods = false)
    @Import(PasswordEncoderBeanRegistrar::class)
    class TestConfiguration
}
