package build.wallet.money

import build.wallet.money.currency.BTC
import build.wallet.money.currency.EUR
import build.wallet.money.currency.USD
import com.ionspin.kotlin.bignum.decimal.BigDecimal
import com.ionspin.kotlin.bignum.decimal.toBigDecimal
import com.ionspin.kotlin.bignum.integer.toBigInteger
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.shouldBe

class MoneyTests : FunSpec({

  context("compare") {
    test("equal") {
      BitcoinMoney.sats(123).compareTo(BitcoinMoney.sats(123)).shouldBe(0)
      (BitcoinMoney.sats(123) == BitcoinMoney.sats(123)).shouldBeTrue()
    }

    test("less than") {
      BitcoinMoney.sats(123).compareTo(BitcoinMoney.sats(124)).shouldBe(-1)
      (BitcoinMoney.sats(123) < BitcoinMoney.sats(124)).shouldBeTrue()
    }

    test("greater than") {
      BitcoinMoney.sats(124).compareTo(BitcoinMoney.sats(123)).shouldBe(1)
      (BitcoinMoney.sats(124) > BitcoinMoney.sats(123)).shouldBeTrue()
    }

    test("less than or equal") {
      BitcoinMoney.sats(123).compareTo(BitcoinMoney.sats(124)).shouldBe(-1)
      (BitcoinMoney.sats(123) <= BitcoinMoney.sats(124)).shouldBeTrue()
    }

    test("greater than or equal") {
      BitcoinMoney.sats(124).compareTo(BitcoinMoney.sats(123)).shouldBe(1)
      (BitcoinMoney.sats(124) >= BitcoinMoney.sats(123)).shouldBeTrue()
    }

    test("throw when comparing different currencies") {
      shouldThrow<IllegalArgumentException> {
        BitcoinMoney.sats(123).compareTo(FiatMoney.usd(dollars = 1.23))
      }
    }
  }

  context("minus operator") {
    test("subtract") {
      BitcoinMoney.sats(123).minus(BitcoinMoney.sats(1)).shouldBe(BitcoinMoney.sats(122))
    }
  }

  context("plus operator") {
    test("add") {
      BitcoinMoney.sats(123).plus(BitcoinMoney.sats(1)).shouldBe(BitcoinMoney.sats(124))
    }
  }

  test("string formatting") {
    BitcoinMoney.sats(123456).toString().shouldBe("Money(BTC,0.00123456)")
    FiatMoney.usd(dollars = 5.0).toString().shouldBe("Money(USD,5)")
    FiatMoney.usd(dollars = 5.2).toString().shouldBe("Money(USD,5.2)")
    FiatMoney.zeroUsd().toString().shouldBe("Money(USD,0)")
  }

  context("convenience constructors") {
    test("zero money") {
      FiatMoney.zero(EUR).shouldBe(FiatMoney(value = BigDecimal.ZERO, currency = EUR))
      FiatMoney.zeroUsd().shouldBe(FiatMoney.zero(currency = USD))
      BitcoinMoney.zero().shouldBe(Money.money(currency = BTC, 0.0.toBigDecimal()))
    }

    test("btc") {
      // 430 sats as Long
      BitcoinMoney.sats(430L).shouldBe(BitcoinMoney(value = 0.0000043.toBigDecimal()))
      // 430 sats as Int
      BitcoinMoney.sats(430).shouldBe(BitcoinMoney(value = 0.0000043.toBigDecimal()))
      // 430 sats as BigInteger
      BitcoinMoney.sats(430.toBigInteger()).shouldBe(BitcoinMoney(value = 0.0000043.toBigDecimal()))

      // 4.2 bitcoin as BigDecimal
      BitcoinMoney.btc(4.2.toBigDecimal()).shouldBe(BitcoinMoney(value = 4.2.toBigDecimal()))
      // 4.2 bitcoin as Double
      BitcoinMoney.btc(4.2).shouldBe(BitcoinMoney(value = 4.2.toBigDecimal()))
    }

    test("usd") {
      // 430 cents as Long
      FiatMoney.usd(cents = 430).shouldBe(Money.money(USD, value = 4.3.toBigDecimal()))
      // 430 cents as BigInteger
      FiatMoney.usd(
        cents = 430.toBigInteger()
      ).shouldBe(Money.money(USD, value = 4.3.toBigDecimal()))

      // 4.2 dollars as BigDecimal
      FiatMoney.usd(
        dollars = 4.2.toBigDecimal()
      ).shouldBe(Money.money(USD, value = 4.2.toBigDecimal()))
      // 4.2 dollars as Double
      FiatMoney.usd(dollars = 4.2).shouldBe(Money.money(USD, value = 4.2.toBigDecimal()))
    }

    test("eur") {
      // 430 cents as Long
      FiatMoney.eur(cents = 430).shouldBe(Money.money(EUR, value = 4.3.toBigDecimal()))
      // 430 cents as BigInteger
      FiatMoney.eur(
        cents = 430.toBigInteger()
      ).shouldBe(Money.money(EUR, value = 4.3.toBigDecimal()))

      // 4.2 euros as BigDecimal
      FiatMoney.eur(
        euros = 4.2.toBigDecimal()
      ).shouldBe(Money.money(EUR, value = 4.2.toBigDecimal()))
      // 4.2 euros as Double
      FiatMoney.eur(euros = 4.2).shouldBe(Money.money(EUR, value = 4.2.toBigDecimal()))
    }
  }

  test("negate") {
    FiatMoney.usd(dollars = 4.2).negate().shouldBe(FiatMoney.usd(dollars = -4.2))
    FiatMoney.usd(dollars = -4.2).negate().shouldBe(FiatMoney.usd(dollars = 4.2))
    FiatMoney.zeroUsd().negate().shouldBe(FiatMoney.zeroUsd())
  }

  test("abs") {
    FiatMoney.usd(dollars = 4.2).abs().shouldBe(FiatMoney.usd(dollars = 4.2))
    FiatMoney.usd(dollars = -4.2).abs().shouldBe(FiatMoney.usd(dollars = 4.2))
    FiatMoney.zeroUsd().abs().shouldBe(FiatMoney.zeroUsd())
  }

  context("sumOf extension") {
    data class MoneyWrapper(val amount: BitcoinMoney)

    test("sum of money") {
      listOf(
        MoneyWrapper(BitcoinMoney.sats(2))
      ).sumOf { it.amount }.shouldBe(BitcoinMoney.sats(2))

      listOf(
        MoneyWrapper(BitcoinMoney.sats(1)),
        MoneyWrapper(BitcoinMoney.sats(2)),
        MoneyWrapper(BitcoinMoney.sats(3))
      ).sumOf { it.amount }.shouldBe(BitcoinMoney.sats(6))
    }

    test("cannot sum empty list") {
      shouldThrow<UnsupportedOperationException> {
        emptyList<MoneyWrapper>().sumOf { it.amount }
      }
    }
  }
})
