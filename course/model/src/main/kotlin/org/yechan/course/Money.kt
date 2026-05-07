package org.yechan.course

import java.math.BigDecimal

data class Money(val amount: BigDecimal) {
    init {
        if (amount < BigDecimal.ZERO) throw IllegalArgumentException("금액은 음수일 수 없습니다.")
    }

    operator fun plus(other: Money): Money = Money(this.amount + other.amount)

    operator fun minus(other: Money): Money {
        val result = this.amount - other.amount
        if (result < BigDecimal.ZERO) throw IllegalArgumentException("결과 금액은 음수일 수 없습니다.")
        return Money(result)
    }

    operator fun times(multiplier: Int): Money {
        if (multiplier < 0) throw IllegalArgumentException("곱셈의 배수는 음수일 수 없습니다.")
        return Money(this.amount * BigDecimal(multiplier))
    }

    operator fun div(divisor: Int): Money {
        if (divisor <= 0) throw IllegalArgumentException("나눗셈의 분모는 양수여야 합니다.")
        return Money(this.amount / BigDecimal(divisor))
    }

    override fun toString(): String = "Money(amount=$amount)"

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is Money) return false
        return amount == other.amount
    }

    override fun hashCode(): Int = amount.hashCode()

    companion object {
        val ZERO = Money(BigDecimal.ZERO)
    }
}
