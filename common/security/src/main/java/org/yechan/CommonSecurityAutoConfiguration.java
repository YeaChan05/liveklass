package org.yechan;

import org.springframework.beans.factory.BeanRegistrar;
import org.springframework.beans.factory.BeanRegistry;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.security.autoconfigure.SecurityAutoConfiguration;
import org.springframework.boot.security.autoconfigure.web.servlet.ServletWebSecurityAutoConfiguration;
import org.springframework.context.annotation.Import;
import org.springframework.core.Ordered;
import org.springframework.core.env.Environment;
import org.springframework.security.access.hierarchicalroles.RoleHierarchy;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.AccessDeniedHandler;

@AutoConfiguration(before = {
    SecurityAutoConfiguration.class,
    ServletWebSecurityAutoConfiguration.class,
})
@Import(CommonSecurityBeanRegistrar.class)
public class CommonSecurityAutoConfiguration {
}

class CommonSecurityBeanRegistrar implements BeanRegistrar {
    @Override
    public void register(BeanRegistry registry, Environment env) {
        registry.registerBean(AccessTokenBlacklist.class, spec -> spec
            .fallback()
            .supplier(context -> NoOpAccessTokenBlacklist.INSTANCE));

        registry.registerBean(AuthenticationEntryPoint.class, spec -> spec
            .fallback()
            .supplier(context -> new DefaultAuthenticationEntryPoint()));

        registry.registerBean(AccessDeniedHandler.class, spec -> spec
            .fallback()
            .supplier(context -> new DefaultAccessDeniedHandler()));

        registry.registerBean(AuthorizeHttpRequestsCustomizer.class, spec -> spec.supplier(context ->
            new PrioritizedAuthorizeHttpRequestsCustomizer(
                Ordered.LOWEST_PRECEDENCE,
                registryCustomizer -> registryCustomizer.anyRequest().authenticated()
            )
        ));

        registry.registerBean(SecurityFilterChain.class, spec -> spec.supplier(context -> {
            HttpSecurity http = context.bean(HttpSecurity.class)
                .formLogin(AbstractHttpConfigurer::disable)
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .exceptionHandling(handler -> handler
                    .authenticationEntryPoint(context.bean(AuthenticationEntryPoint.class))
                    .accessDeniedHandler(context.bean(AccessDeniedHandler.class)));

            RoleHierarchy roleHierarchy = context.beanProvider(RoleHierarchy.class).getIfAvailable();
            if (roleHierarchy != null) {
                http.setSharedObject(RoleHierarchy.class, roleHierarchy);
            }

            http.authorizeHttpRequests(registryCustomizer ->
                context.beanProvider(AuthorizeHttpRequestsCustomizer.class)
                    .orderedStream()
                    .forEach(customizer -> customizer.customize(registryCustomizer))
            );

            context.beanProvider(SecurityFilterChainCustomizer.class)
                .orderedStream()
                .forEach(customizer -> {
                    try {
                        customizer.customize(http);
                    } catch (Exception e) {
                        throw new IllegalStateException("SecurityFilterChain customization failed", e);
                    }
                });

            return http.build();
        }));
    }
}
