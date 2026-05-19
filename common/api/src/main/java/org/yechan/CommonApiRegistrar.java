package org.yechan;

import java.util.List;
import org.springframework.beans.factory.BeanRegistrar;
import org.springframework.beans.factory.BeanRegistry;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.context.annotation.Import;
import org.springframework.core.env.Environment;
import org.springframework.http.converter.HttpMessageConverters;
import org.springframework.web.accept.HeaderApiVersionResolver;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.servlet.config.annotation.ApiVersionConfigurer;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import tools.jackson.databind.ObjectMapper;

@Import(GlobalExceptionHandler.class)
public class CommonApiRegistrar {
}

@AutoConfiguration
class CommonApiBeanRegistrar implements BeanRegistrar {
    @Override
    public void register(BeanRegistry registry, Environment env) {
        registry.registerBean(LoginUserIdArgumentResolver.class, spec -> spec.supplier(context -> new LoginUserIdArgumentResolver()));

        registry.registerBean(WebMvcConfigurer.class, spec -> spec.supplier(context -> {
            LoginUserIdArgumentResolver loginUserIdArgumentResolver = context.bean(LoginUserIdArgumentResolver.class);
            ObjectMapper objectMapper = context.beanProvider(ObjectMapper.class).getIfAvailable();
            List<ApiHateoasLinkProvider> linkProviders = context.beanProvider(ApiHateoasLinkProvider.class).stream().toList();

            return new WebMvcConfigurer() {
                @Override
                public void addArgumentResolvers(List<HandlerMethodArgumentResolver> resolvers) {
                    resolvers.add(loginUserIdArgumentResolver);
                }

                @Override
                public void configureMessageConverters(HttpMessageConverters.ServerBuilder builder) {
                    builder.configureMessageConvertersList(converters -> {
                        if (objectMapper != null && !linkProviders.isEmpty()) {
                            converters.add(0, new ApiHateoasHttpMessageConverter(objectMapper, linkProviders));
                        }
                    });
                }

                @Override
                public void configureApiVersioning(ApiVersionConfigurer configurer) {
                    configurer.useVersionResolver(new HeaderApiVersionResolver(HeaderConst.API_VERSION_HEADER));
                }
            };
        }));
    }
}
