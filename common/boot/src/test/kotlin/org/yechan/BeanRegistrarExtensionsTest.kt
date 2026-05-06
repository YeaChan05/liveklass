package org.yechan

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.BeanRegistrarDsl
import org.springframework.context.annotation.AnnotationConfigApplicationContext
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Import
import org.springframework.core.env.MapPropertySource

class BeanRegistrarExtensionsTest {
    @Test
    fun `matchIfMissing true면 property가 없어도 bean을 등록한다`() {
        val context = createContext(TestMatchIfMissingConfiguration::class.java)

        assertThat(context.containsBean("sample")).isTrue()

        context.close()
    }

    @Test
    fun `property 값이 다르면 bean을 등록하지 않는다`() {
        val context = createContext(
            TestExactMatchConfiguration::class.java,
            "sample.feature.enabled" to "false",
        )

        assertThat(context.containsBean("sample")).isFalse()

        context.close()
    }

    @Test
    fun `property 값이 같으면 bean을 등록한다`() {
        val context = createContext(
            TestExactMatchConfiguration::class.java,
            "sample.feature.enabled" to "true",
        )

        assertThat(context.containsBean("sample")).isTrue()

        context.close()
    }

    @Test
    fun `registerIf true이면 첫 분기만 등록한다`() {
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
    fun `registerIf false이면 else if 분기를 등록한다`() {
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
    fun `모든 조건이 false이면 else 분기를 등록한다`() {
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

    class MatchIfMissingBeanRegistrar :
        BeanRegistrarDsl({
            whenPropertyEnabled("sample.feature", "enabled", matchIfMissing = true) {
                registerBean<String>("sample") { "enabled" }
            }
        })

    class ExactMatchBeanRegistrar :
        BeanRegistrarDsl({
            whenPropertyEnabled("sample.feature", "enabled") {
                registerBean<String>("sample") { "enabled" }
            }
        })

    class ConditionalChainBeanRegistrar :
        BeanRegistrarDsl({
            registerIf(
                predicate = { it.getProperty("sample.feature.mode") == "alpha" },
            ) {
                registerBean<String>("alpha") { "alpha" }
            }
                .orElseIf(
                    predicate = { it.getProperty("sample.feature.mode") == "beta" },
                ) {
                    registerBean<String>("beta") { "beta" }
                }
                .orElse {
                    registerBean<String>("fallback") { "fallback" }
                }
        })
}
