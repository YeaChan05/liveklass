package org.yechan;

import java.util.function.BiConsumer;
import java.util.function.Predicate;
import org.springframework.beans.factory.BeanRegistry;
import org.springframework.core.env.Environment;

public final class ConditionalRegistration {
    private final BeanRegistry registry;
    private final Environment env;
    private boolean matched;

    ConditionalRegistration(BeanRegistry registry, Environment env, boolean matched) {
        this.registry = registry;
        this.env = env;
        this.matched = matched;
    }

    public ConditionalRegistration orElseIf(
        Predicate<Environment> predicate,
        BiConsumer<BeanRegistry, Environment> registration
    ) {
        if (!matched && predicate.test(env)) {
            registration.accept(registry, env);
            matched = true;
        }
        return this;
    }

    public void orElse(BiConsumer<BeanRegistry, Environment> registration) {
        if (!matched) {
            registration.accept(registry, env);
            matched = true;
        }
    }
}
