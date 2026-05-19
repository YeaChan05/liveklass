package org.yechan

import org.assertj.core.api.Assertions
import org.junit.jupiter.api.Test
import org.springframework.context.annotation.AnnotationConfigApplicationContext
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Import
import org.springframework.security.crypto.password.PasswordEncoder

class PasswordEncoderBeanRegistrarTest {
    @Test
    fun `자동 설정은 비밀번호 인코더 관련 빈을 등록한다`() {
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

    @Test
    fun `사용자 비밀번호 인코더 빈이 있으면 기본 빈은 후순위로 밀린다`() {
        val context = AnnotationConfigApplicationContext().apply {
            register(CustomPasswordConfiguration::class.java)
            refresh()
        }

        Assertions.assertThat(context.getBean(PasswordEncoder::class.java))
            .isInstanceOf(TestPasswordEncoder::class.java)
        Assertions.assertThat(context.getBean(PasswordHashEncoder::class.java))
            .isInstanceOf(TestPasswordHashEncoder::class.java)

        context.close()
    }

    @Configuration(proxyBeanMethods = false)
    @Import(PasswordEncoderBeanRegistrar::class)
    class TestConfiguration

    @Configuration(proxyBeanMethods = false)
    @Import(PasswordEncoderBeanRegistrar::class)
    class CustomPasswordConfiguration {
        @Bean
        fun passwordEncoder(): PasswordEncoder = TestPasswordEncoder()

        @Bean
        fun passwordHashEncoder(): PasswordHashEncoder = TestPasswordHashEncoder()
    }

    private class TestPasswordEncoder : PasswordEncoder {
        override fun encode(rawPassword: CharSequence?): String = "test:$rawPassword"

        override fun matches(
            rawPassword: CharSequence?,
            encodedPassword: String?,
        ): Boolean = encodedPassword == encode(rawPassword)
    }

    private class TestPasswordHashEncoder : PasswordHashEncoder {
        override fun encode(password: String): String = "hash:$password"

        override fun matches(
            password: String,
            encodedPassword: String,
        ): Boolean = encodedPassword == encode(password)
    }
}
