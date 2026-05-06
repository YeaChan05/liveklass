package org.yechan

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.context.annotation.AnnotationConfigApplicationContext
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Import
import org.springframework.web.method.support.HandlerMethodArgumentResolver
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer

class CommonApiBeanRegistrarTest {
    @Test
    fun `자동 설정은 공통 api 빈을 등록한다`() {
        val context = AnnotationConfigApplicationContext().apply {
            register(TestConfiguration::class.java)
            refresh()
        }

        val configurer = context.getBean(WebMvcConfigurer::class.java)
        val resolvers = mutableListOf<HandlerMethodArgumentResolver>()

        configurer.addArgumentResolvers(resolvers)

        assertThat(context.getBean(GlobalExceptionHandler::class.java)).isNotNull
        assertThat(context.getBean(LoginUserIdArgumentResolver::class.java)).isNotNull
        assertThat(resolvers).singleElement().isInstanceOf(LoginUserIdArgumentResolver::class.java)

        context.close()
    }

    @Configuration(proxyBeanMethods = false)
    @Import(CommonApiRegistrar::class, CommonApiBeanRegistrar::class)
    class TestConfiguration
}
