package org.yechan;

import java.time.Clock;
import org.springframework.beans.factory.BeanRegistrar;
import org.springframework.beans.factory.BeanRegistry;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.context.annotation.Import;
import org.springframework.core.env.Environment;
import org.springframework.http.converter.HttpMessageConverters;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import tools.jackson.databind.ObjectMapper;

@AutoConfiguration
@Import(ApiResponseMessageConverterBeanRegistrar.class)
public class ApiResponseMessageConverterAutoConfiguration {
}

class ApiResponseMessageConverterBeanRegistrar implements BeanRegistrar {
    @Override
    public void register(BeanRegistry registry, Environment env) {
        registry.registerBean(WebMvcConfigurer.class, spec -> spec.supplier(context -> {
            ObjectMapper objectMapper = context.bean(ObjectMapper.class);
            Clock clock = context.beanProvider(Clock.class).getIfAvailable(Clock::systemUTC);

            return new WebMvcConfigurer() {
                @Override
                public void configureMessageConverters(HttpMessageConverters.ServerBuilder builder) {
                    builder.configureMessageConvertersList(converters ->
                        converters.add(0, new ApiResponseHttpMessageConverter(objectMapper, clock)));
                }
            };
        }));
    }
}
