package org.yechan

import org.springframework.beans.factory.BeanRegistrarDsl
import org.springframework.core.env.Environment

/**
 * [registerIf] 이후 `else if` / `else` 체인을 이어 붙일 때 사용하는 내부 DSL.
 *
 * 예:
 * ```kotlin
 * class SampleModeBeanRegistrar :
 *     BeanRegistrarDsl({
 *         registerIf(
 *             predicate = { env ->
 *                 env.getProperty("sample.feature.mode") == "alpha"
 *             },
 *         ) {
 *             registerBean<String>("alpha") { "alpha" }
 *         }.orElseIf(
 *             predicate = { env ->
 *                 env.getProperty("sample.feature.mode") == "beta"
 *             },
 *         ) {
 *             registerBean<String>("beta") { "beta" }
 *         }.orElse {
 *             registerBean<String>("fallback") { "fallback" }
 *         }
 *     })
 * ```
 */
class BeanRegistrarConditionalDsl internal constructor(
    private val registrarDsl: BeanRegistrarDsl,
    private var matched: Boolean,
) {
    fun orElseIf(
        predicate: (Environment) -> Boolean,
        block: BeanRegistrarDsl.() -> Unit,
    ): BeanRegistrarConditionalDsl {
        if (!matched && predicate(registrarDsl.env)) {
            registrarDsl.block()
            matched = true
        }
        return this
    }

    fun orElse(block: BeanRegistrarDsl.() -> Unit) {
        if (!matched) {
            registrarDsl.block()
            matched = true
        }
    }
}

/**
 * [Environment] 값을 기준으로 registrar 블록을 조건부로 실행한다.
 *
 * 예:
 * ```kotlin
 * class SampleModeBeanRegistrar :
 *     BeanRegistrarDsl({
 *         registerIf(
 *             predicate = { env ->
 *                 env.getProperty("sample.feature.mode") == "alpha"
 *             },
 *         ) {
 *             registerBean<String>("alpha") { "alpha" }
 *         }.orElseIf(
 *             predicate = { env ->
 *                 env.getProperty("sample.feature.mode") == "beta"
 *             },
 *         ) {
 *             registerBean<String>("beta") { "beta" }
 *         }.orElse {
 *             registerBean<String>("fallback") { "fallback" }
 *         }
 *     })
 * ```
 */
fun BeanRegistrarDsl.registerIf(
    predicate: (Environment) -> Boolean,
    block: BeanRegistrarDsl.() -> Unit,
): BeanRegistrarConditionalDsl {
    val matched = predicate(env)
    if (matched) {
        block()
    }
    return BeanRegistrarConditionalDsl(this, matched)
}

/**
 * 단일 property 값을 기준으로 registrar 블록을 실행한다.
 *
 * 예:
 * ```kotlin
 * class TransferOutboxBeanRegistrar :
 *     BeanRegistrarDsl({
 *         whenPropertyEnabled(
 *             prefix = "transfer.outbox",
 *             name = "enabled",
 *             matchIfMissing = true,
 *         ) {
 *             registerBean<TransferEventPublisher> {
 *                 TransferEventPublisherImpl(
 *                     bean<RabbitTemplate>(),
 *                     bean<TransferEventPublisherProperties>(),
 *                 )
 *             }
 *         }
 *     })
 * ```
 */
fun BeanRegistrarDsl.whenPropertyEnabled(
    prefix: String,
    name: String,
    havingValue: String = "true",
    matchIfMissing: Boolean = false,
    block: BeanRegistrarDsl.() -> Unit,
) {
    val key = "$prefix.$name"
    val value = env.getProperty(key)
    val enabled = value?.equals(havingValue, ignoreCase = true) ?: matchIfMissing

    if (enabled) {
        block()
    }
}
