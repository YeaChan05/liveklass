package org.yechan

import org.springframework.beans.factory.BeanRegistrarDsl
import org.springframework.boot.autoconfigure.AutoConfiguration
import org.springframework.http.converter.HttpMessageConverters
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer
import tools.jackson.databind.ObjectMapper
import java.time.Clock

@AutoConfiguration
class ApiResponseMessageConverterAutoConfiguration :
    BeanRegistrarDsl({
        registerBean<WebMvcConfigurer> {
            val objectMapper = bean<ObjectMapper>()
            val clock = bean<Clock>()

            object : WebMvcConfigurer {
                override fun configureMessageConverters(builder: HttpMessageConverters.ServerBuilder) {
                    builder.configureMessageConvertersList { converters ->
                        converters.add(0, ApiResponseHttpMessageConverter(objectMapper, clock))
                    }
                }
            }
        }
    })
