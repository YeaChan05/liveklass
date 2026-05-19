package org.yechan

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.BeanRegistrar
import org.springframework.beans.factory.BeanRegistry
import org.springframework.context.annotation.AnnotationConfigApplicationContext
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Import
import org.springframework.core.env.Environment
import org.springframework.core.env.MapPropertySource
import java.util.function.BiConsumer
import java.util.function.Predicate

class BeanRegistrarExtensionsTest {
    @Test
    fun `값이 없을 때 일치 옵션이 켜져 있으면 속성이 없어도 빈을 등록한다`() {
        val context = createContext(TestMatchIfMissingConfiguration::class.java)

        assertThat(context.containsBean("sample")).isTrue()

        context.close()
    }

    @Test
    fun `속성 값이 다르면 빈을 등록하지 않는다`() {
        val context = createContext(
            TestExactMatchConfiguration::class.java,
            "sample.feature.enabled" to "false",
        )

        assertThat(context.containsBean("sample")).isFalse()

        context.close()
    }

    @Test
    fun `속성 값이 같으면 빈을 등록한다`() {
        val context = createContext(
            TestExactMatchConfiguration::class.java,
            "sample.feature.enabled" to "true",
        )

        assertThat(context.containsBean("sample")).isTrue()

        context.close()
    }

    @Test
    fun `조건 등록이 참이면 첫 분기만 등록한다`() {
        val context = createContext(
            TestConditionalChainConfiguration::class.java,
            "sample.feature.mode" to "alpha",
        )

        assertThat(context.containsBean("alpha")).isTrue()
        assertThat(context.containsBean("beta")).isFalse()
        assertThat(context.containsBean("fallback")).isFalse()

        context.close()
    }

    @Test
    fun `조건 등록이 거짓이면 다음 조건 분기를 등록한다`() {
        val context = createContext(
            TestConditionalChainConfiguration::class.java,
            "sample.feature.mode" to "beta",
        )

        assertThat(context.containsBean("alpha")).isFalse()
        assertThat(context.containsBean("beta")).isTrue()
        assertThat(context.containsBean("fallback")).isFalse()

        context.close()
    }

    @Test
    fun `모든 조건이 거짓이면 기본 분기를 등록한다`() {
        val context = createContext(
            TestConditionalChainConfiguration::class.java,
            "sample.feature.mode" to "other",
        )

        assertThat(context.containsBean("alpha")).isFalse()
        assertThat(context.containsBean("beta")).isFalse()
        assertThat(context.containsBean("fallback")).isTrue()

        context.close()
    }

    private fun createContext(
        configuration: Class<*>,
        vararg properties: Pair<String, String>,
    ): AnnotationConfigApplicationContext = AnnotationConfigApplicationContext()
        .apply {
            if (properties.isNotEmpty()) {
                environment.propertySources.addFirst(MapPropertySource("test", mapOf(*properties)))
            }
            register(configuration)
            refresh()
        }

    @Configuration(proxyBeanMethods = false)
    @Import(MatchIfMissingBeanRegistrar::class)
    class TestMatchIfMissingConfiguration

    @Configuration(proxyBeanMethods = false)
    @Import(ExactMatchBeanRegistrar::class)
    class TestExactMatchConfiguration

    @Configuration(proxyBeanMethods = false)
    @Import(ConditionalChainBeanRegistrar::class)
    class TestConditionalChainConfiguration

    class MatchIfMissingBeanRegistrar : BeanRegistrar {
        override fun register(
            registry: BeanRegistry,
            env: Environment,
        ) {
            BeanRegistrarUtils.whenPropertyEnabled(
                registry,
                env,
                "sample.feature",
                "enabled",
                "true",
                true,
                BiConsumer { beanRegistry, _ -> registerStringBean(beanRegistry, "sample", "enabled") },
            )
        }
    }

    class ExactMatchBeanRegistrar : BeanRegistrar {
        override fun register(
            registry: BeanRegistry,
            env: Environment,
        ) {
            BeanRegistrarUtils.whenPropertyEnabled(
                registry,
                env,
                "sample.feature",
                "enabled",
                BiConsumer { beanRegistry, _ -> registerStringBean(beanRegistry, "sample", "enabled") },
            )
        }
    }

    class ConditionalChainBeanRegistrar : BeanRegistrar {
        override fun register(
            registry: BeanRegistry,
            env: Environment,
        ) {
            BeanRegistrarUtils.registerIf(
                registry,
                env,
                Predicate { it.getProperty("sample.feature.mode") == "alpha" },
                BiConsumer { beanRegistry, _ -> registerStringBean(beanRegistry, "alpha", "alpha") },
            )
                .orElseIf(
                    Predicate { it.getProperty("sample.feature.mode") == "beta" },
                    BiConsumer { beanRegistry, _ -> registerStringBean(beanRegistry, "beta", "beta") },
                )
                .orElse(
                    BiConsumer { beanRegistry, _ -> registerStringBean(beanRegistry, "fallback", "fallback") },
                )
        }
    }

    companion object {
        private fun registerStringBean(
            registry: BeanRegistry,
            name: String,
            value: String,
        ) {
            registry.registerBean(name, String::class.java) { spec ->
                spec.supplier { value }
            }
        }
    }
}
