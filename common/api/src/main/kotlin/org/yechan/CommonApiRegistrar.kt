package org.yechan

import org.springframework.beans.factory.BeanRegistrarDsl
import org.springframework.boot.autoconfigure.AutoConfiguration
import org.springframework.context.annotation.Import
import org.springframework.web.accept.HeaderApiVersionResolver
import org.springframework.web.method.support.HandlerMethodArgumentResolver
import org.springframework.web.servlet.config.annotation.ApiVersionConfigurer
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer

@Import(GlobalExceptionHandler::class)
class CommonApiRegistrar

@AutoConfiguration
class CommonApiBeanRegistrar :
    BeanRegistrarDsl({
        registerBean<LoginUserIdArgumentResolver> {
            LoginUserIdArgumentResolver()
        }

        registerBean<WebMvcConfigurer> {
            val loginUserIdArgumentResolver = bean<LoginUserIdArgumentResolver>()

            object : WebMvcConfigurer {
                override fun addArgumentResolvers(resolvers: MutableList<HandlerMethodArgumentResolver>) {
                    resolvers.add(loginUserIdArgumentResolver)
                }

                override fun configureApiVersioning(configurer: ApiVersionConfigurer) {
                    configurer.useVersionResolver(
                        HeaderApiVersionResolver(API_VERSION_HEADER),
                    )
                }
            }
        }
    })
