package org.yechan.course

import java.math.BigDecimal
import java.math.RoundingMode

data class Money(
    val amount: BigDecimal,
) {
    init {
        if (amount < BigDecimal.ZERO) throw CourseInvalidStateException("금액은 음수일 수 없습니다.")
    }

    constructor(amount: Long) : this(BigDecimal.valueOf(amount))

    constructor(amount: Int) : this(amount.toLong())

    operator fun plus(other: Money): Money = Money(this.amount + other.amount)

    operator fun minus(other: Money): Money {
        val result = this.amount - other.amount

        if (result < BigDecimal.ZERO) throw CourseInvalidStateException("결과 금액은 음수일 수 없습니다.")

        return Money(result)
    }

    operator fun times(multiplier: Int): Money {
        if (multiplier < 0) throw CourseInvalidStateException("곱셈의 배수는 음수일 수 없습니다.")

        return Money(this.amount.multiply(BigDecimal.valueOf(multiplier.toLong())))
    }

    operator fun times(multiplier: Long): Money {
        if (multiplier < 0) throw CourseInvalidStateException("곱셈의 배수는 음수일 수 없습니다.")

        return Money(this.amount.multiply(BigDecimal.valueOf(multiplier)))
    }

    operator fun div(divisor: Int): Money {
        if (divisor <= 0) throw CourseInvalidStateException("나눗셈의 분모는 양수여야 합니다.")

        return Money(
            this.amount.divide(
                BigDecimal.valueOf(divisor.toLong()),
                RoundingMode.DOWN,
            ),
        )
    }

    operator fun div(divisor: Long): Money {
        if (divisor <= 0) throw CourseInvalidStateException("나눗셈의 분모는 양수여야 합니다.")

        return Money(
            this.amount.divide(
                BigDecimal.valueOf(divisor),
                RoundingMode.DOWN,
            ),
        )
    }

    override fun toString(): String = "Money(amount=$amount)"

    companion object {
        val ZERO = Money(BigDecimal.ZERO)
    }
}
