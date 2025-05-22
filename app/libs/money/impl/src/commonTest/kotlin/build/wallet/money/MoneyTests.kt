package build.wallet.money

import build.wallet.money.currency.EUR
import build.wallet.money.currency.GBP
import build.wallet.money.currency.USD
import com.ionspin.kotlin.bignum.decimal.BigDecimal.Companion.ZERO
import com.ionspin.kotlin.bignum.decimal.toBigDecimal
import com.ionspin.kotlin.bignum.integer.toBigInteger
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.booleans.shouldBeFalse
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.equals.shouldBeEqual
import io.kotest.matchers.equals.shouldNotBeEqual
import io.kotest.matchers.shouldBe
import io.kotest.property.Arb
import io.kotest.property.Exhaustive
import io.kotest.property.arbitrary.filter
import io.kotest.property.arbitrary.int
import io.kotest.property.arbitrary.negativeDouble
import io.kotest.property.arbitrary.positiveDouble
import io.kotest.property.checkAll
import io.kotest.property.exhaustive.collection

class MoneyTests : FunSpec({

  test("Properties") {
    checkAll(Exhaustive.collection(allTestingFiatCurrencies)) { currency ->
      // Zero amount
      with(FiatMoney(currency, ZERO)) {
        isZero.shouldBeTrue()
        isNegative.shouldBeFalse()
        isPositive.shouldBeFalse()
        isWholeNumber.shouldBeTrue()
      }

      // Positive amounts
      checkAll(Arb.positiveDouble().filteringEdgeCases()) { value ->
        with(FiatMoney(currency, value.toBigDecimal())) {
          isZero.shouldBeFalse()
          isNegative.shouldBeFalse()
          isPositive.shouldBeTrue()
        }
      }

      // Negative amounts
      checkAll(Arb.negativeDouble().filteringEdgeCases()) { value ->
        with(FiatMoney(currency, value.toBigDecimal())) {
          isZero.shouldBeFalse()
          isNegative.shouldBeTrue()
          isPositive.shouldBeFalse()
        }
      }

      // Whole numbers
      checkAll(Arb.int()) { value ->
        FiatMoney(currency, value.toBigDecimal()).isWholeNumber.shouldBeTrue()
      }
    }
  }

  test("Fractional Unit Value") {
    val currencyWithZeroFractionalDigits = listOf(testJPY)
    for (currency in currencyWithZeroFractionalDigits) {
      FiatMoney(currency, 1.toBigDecimal()).fractionalUnitValue.shouldBe(1.toBigInteger())
    }

    val currencyWithTwoFractionalDigits = listOf(testAUD, testCAD, EUR, GBP, USD)
    for (currency in currencyWithTwoFractionalDigits) {
      FiatMoney(currency, 1.toBigDecimal()).fractionalUnitValue.shouldBe(100.toBigInteger())
    }

    val currencyWithThreeFractionalDigits = listOf(testKWD)
    for (currency in currencyWithThreeFractionalDigits) {
      FiatMoney(currency, 1.toBigDecimal()).fractionalUnitValue.shouldBe(1000.toBigInteger())
    }

    BitcoinMoney.btc(1.0).fractionalUnitValue.shouldBe(100000000.toBigInteger())
  }

  test("Addition") {
    checkAll(Exhaustive.collection(allTestingFiatCurrencies)) { currency ->
      (FiatMoney(currency, ZERO) + FiatMoney(currency, ZERO))
        .value.shouldBe(ZERO)

      (FiatMoney(currency, ZERO) + FiatMoney(currency, 4.toBigDecimal()))
        .value.shouldBe(4.toBigDecimal())

      (FiatMoney(currency, 4.toBigDecimal()) + FiatMoney(currency, (-2).toBigDecimal()))
        .value.shouldBe(2.toBigDecimal())
    }

    (BitcoinMoney.zero() + BitcoinMoney.zero())
      .value.shouldBe(ZERO)

    (BitcoinMoney.zero() + BitcoinMoney.btc(4.0))
      .value.shouldBe(4.toBigDecimal())

    (BitcoinMoney.btc(4.0) + BitcoinMoney.btc(-2.0))
      .value.shouldBe(2.toBigDecimal())
  }

  test("Subtraction") {
    checkAll(Exhaustive.collection(allTestingFiatCurrencies)) { currency ->
      (FiatMoney(currency, ZERO) - FiatMoney(currency, ZERO))
        .value.shouldBe(ZERO)

      (FiatMoney(currency, ZERO) - FiatMoney(currency, 4.toBigDecimal())).value
        .shouldBe((-4).toBigDecimal())

      (FiatMoney(currency, 4.toBigDecimal()) - FiatMoney(currency, (-2).toBigDecimal()))
        .value.shouldBe(6.toBigDecimal())
    }

    (BitcoinMoney.zero() - BitcoinMoney.zero())
      .value.shouldBe(ZERO)

    (BitcoinMoney.zero() - BitcoinMoney.btc(4.0))
      .value.shouldBe((-4).toBigDecimal())

    (BitcoinMoney.btc(4.0) - BitcoinMoney.btc(-2.0))
      .value.shouldBe(6.toBigDecimal())
  }

  test("Equality") {
    BitcoinMoney.btc(1.23).shouldBeEqual(BitcoinMoney.btc(1.23))
    FiatMoney.usd(123).shouldBeEqual(FiatMoney.usd(123))
    BitcoinMoney.zero().shouldBeEqual(FiatMoney.zeroUsd())
    BitcoinMoney.sats(123).shouldNotBeEqual(FiatMoney.usd(123))
  }

  test("Fractional Unit Constructor") {
    val currencyWithZeroFractionalDigits = listOf(testJPY)
    for (currency in currencyWithZeroFractionalDigits) {
      FiatMoney(currency, 1.toBigDecimal())
        .fractionalUnitValue.intValue().shouldBe(1)
    }

    val currencyWithTwoFractionalDigits = listOf(testAUD, testCAD, EUR, GBP, USD)
    for (currency in currencyWithTwoFractionalDigits) {
      FiatMoney(currency, 1.toBigDecimal())
        .fractionalUnitValue.intValue().shouldBe(100)
    }

    val currencyWithThreeFractionalDigits = listOf(testKWD)
    for (currency in currencyWithThreeFractionalDigits) {
      FiatMoney(currency, 1.toBigDecimal())
        .fractionalUnitValue.intValue().shouldBe(1000)
    }

    BitcoinMoney.btc(1.0)
      .fractionalUnitValue.intValue().shouldBe(100000000)
  }
})

/** Filters out Nan and infinity values. */
private fun Arb<Double>.filteringEdgeCases(): Arb<Double> =
  this.filter {
    it == it && it != Double.POSITIVE_INFINITY && it != Double.NEGATIVE_INFINITY
  }
