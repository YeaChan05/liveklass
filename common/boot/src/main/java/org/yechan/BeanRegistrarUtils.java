package org.yechan;

import java.util.function.BiConsumer;
import java.util.function.Predicate;
import org.springframework.beans.factory.BeanRegistry;
import org.springframework.core.env.Environment;

public final class BeanRegistrarUtils {
    private BeanRegistrarUtils() {
    }

    public static ConditionalRegistration registerIf(
        BeanRegistry registry,
        Environment env,
        Predicate<Environment> predicate,
        BiConsumer<BeanRegistry, Environment> registration
    ) {
        boolean matched = predicate.test(env);
        if (matched) {
            registration.accept(registry, env);
        }
        return new ConditionalRegistration(registry, env, matched);
    }

    public static void whenPropertyEnabled(
        BeanRegistry registry,
        Environment env,
        String prefix,
        String name,
        BiConsumer<BeanRegistry, Environment> registration
    ) {
        whenPropertyEnabled(registry, env, prefix, name, "true", false, registration);
    }

    public static void whenPropertyEnabled(
        BeanRegistry registry,
        Environment env,
        String prefix,
        String name,
        String havingValue,
        boolean matchIfMissing,
        BiConsumer<BeanRegistry, Environment> registration
    ) {
        String value = env.getProperty(prefix + "." + name);
        boolean enabled = value == null ? matchIfMissing : value.equalsIgnoreCase(havingValue);
        if (enabled) {
            registration.accept(registry, env);
        }
    }
}
