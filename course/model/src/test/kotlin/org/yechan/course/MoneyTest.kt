package org.yechan.course

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import java.math.BigDecimal

class MoneyTest {

    @Test
    fun `음수 금액으로 Money를 생성할 수 없다`() {
        assertThatThrownBy {
            Money(-100)
        }.isInstanceOf(CourseInvalidStateException::class.java)
    }

    @Test
    fun `Money를 더할 수 있다`() {
        val money1 = Money(1000)
        val money2 = Money(500)

        val result = money1 + money2

        assertThat(result.amount)
            .isEqualTo(BigDecimal.valueOf(1500))
    }

    @Test
    fun `Money를 뺄 수 있다`() {
        val money1 = Money(1000)
        val money2 = Money(300)

        val result = money1 - money2

        assertThat(result.amount)
            .isEqualTo(BigDecimal.valueOf(700))
    }

    @Test
    fun `결과 금액이 음수가 되는 뺄셈은 불가능하다`() {
        val money1 = Money(100)
        val money2 = Money(200)

        assertThatThrownBy {
            money1 - money2
        }.isInstanceOf(CourseInvalidStateException::class.java)
    }

    @Test
    fun `정수 배수만큼 곱셈할 수 있다`() {
        val money = Money(1000)

        val result = money * 3

        assertThat(result.amount)
            .isEqualTo(BigDecimal.valueOf(3000))
    }

    @Test
    fun `Long 배수만큼 곱셈할 수 있다`() {
        val money = Money(1000)

        val result = money * 5L

        assertThat(result.amount)
            .isEqualTo(BigDecimal.valueOf(5000))
    }

    @Test
    fun `음수 배수로 곱셈할 수 없다`() {
        val money = Money(1000)

        assertThatThrownBy {
            money * -1
        }.isInstanceOf(CourseInvalidStateException::class.java)
    }

    @Test
    fun `정수로 나눌 수 있다`() {
        val money = Money(1000)

        val result = money / 4

        assertThat(result.amount)
            .isEqualTo(BigDecimal.valueOf(250))
    }

    @Test
    fun `Long으로 나눌 수 있다`() {
        val money = Money(1000)

        val result = money / 5L

        assertThat(result.amount)
            .isEqualTo(BigDecimal.valueOf(200))
    }

    @Test
    fun `0으로 나눌 수 없다`() {
        val money = Money(1000)

        assertThatThrownBy {
            money / 0
        }.isInstanceOf(CourseInvalidStateException::class.java)
    }

    @Test
    fun `ZERO 상수는 0원을 의미한다`() {
        assertThat(Money.ZERO.amount)
            .isEqualTo(BigDecimal.ZERO)
    }

    @Test
    fun `같은 금액이면 동등하다`() {
        val money1 = Money(1000)
        val money2 = Money(1000)

        assertThat(money1)
            .isEqualTo(money2)
    }
}
