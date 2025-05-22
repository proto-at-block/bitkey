package build.wallet.money

import build.wallet.money.currency.CryptoCurrency
import build.wallet.money.currency.Currency
import build.wallet.money.currency.FiatCurrency
import com.ionspin.kotlin.bignum.decimal.BigDecimal
import com.ionspin.kotlin.bignum.integer.BigInteger
import dev.zacsweers.redacted.annotations.Redacted

@Suppress("TooManyFunctions")
@Redacted
sealed interface Money {
  /** The currency of the amount */
  val currency: Currency

  /**
   * The amount of money.
   *
   * Can be fractional beyond what the corresponding
   * [Currency] supports (i.e. 1.125 is valid for USD).
   * Callers should perform rounding and formatting.
   */
  val value: BigDecimal

  fun copy(
    currency: Currency,
    value: BigDecimal,
  ): Money {
    return when (this) {
      is FiatMoney -> this.copy(value = value)
      is BitcoinMoney -> this.copy(value = value)
    }
  }

  /**
   * The monetary amount in the fractional unit denomination.
   * The amount will be rounded to a long if necessary.
   */
  val fractionalUnitValue: BigInteger
    get() = currency.fractionalUnitValueFromUnitValue(value)

  val isZero get() = value.isZero()
  val isNegative get() = value.isNegative
  val isPositive get() = value.isPositive

  /**
   * Whether or not the monetary amount is a whole number.
   * Always true for currencies that don't support fractional amounts.
   */
  val isWholeNumber get() = value.isWholeNumber()

  fun rounded() =
    copy(
      currency = currency,
      value =
        value.roundSignificand(
          decimalMode = currency.decimalMode()
        )
    )

  operator fun compareTo(other: Money): Int {
    require(this.currency == other.currency)
    return this.value.compareTo(other.value)
  }

  companion object {
    val Comparator =
      Comparator<Money> { a, b ->
        a.compareTo(b)
      }

    fun money(
      currency: Currency,
      value: BigDecimal,
    ) = when (currency) {
      is FiatCurrency -> FiatMoney(currency = currency, value = value)
      is CryptoCurrency -> BitcoinMoney(value = value)
    }
  }
}

/**
 * Negate the amount value of the [Money] by inverting the sign.
 */
fun Money.negate(): Money =
  copy(
    currency = currency,
    value = value.negate()
  )

/**
 * Returns the absolute value of the [Money].
 */
fun Money.abs(): Money =
  copy(
    currency = currency,
    value = value.abs()
  )
