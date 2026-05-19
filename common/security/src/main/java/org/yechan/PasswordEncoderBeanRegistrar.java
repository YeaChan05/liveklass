package org.yechan;

import org.springframework.beans.factory.BeanRegistrar;
import org.springframework.beans.factory.BeanRegistry;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.core.env.Environment;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

@AutoConfiguration
public class PasswordEncoderBeanRegistrar implements BeanRegistrar {
    @Override
    public void register(BeanRegistry registry, Environment env) {
        registry.registerBean(PasswordEncoder.class, spec -> spec
            .fallback()
            .supplier(context -> new BCryptPasswordEncoder()));

        registry.registerBean(PasswordHashEncoder.class, spec -> spec
            .fallback()
            .supplier(context -> new BcryptPasswordHashEncoder(context.bean(PasswordEncoder.class))));
    }
}
