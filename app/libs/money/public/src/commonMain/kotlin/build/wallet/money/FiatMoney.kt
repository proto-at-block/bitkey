package build.wallet.money

import build.wallet.money.currency.EUR
import build.wallet.money.currency.FiatCurrency
import build.wallet.money.currency.USD
import com.ionspin.kotlin.bignum.decimal.BigDecimal
import com.ionspin.kotlin.bignum.decimal.toBigDecimal
import com.ionspin.kotlin.bignum.integer.BigInteger
import com.ionspin.kotlin.bignum.integer.toBigInteger

data class FiatMoney(
  override val currency: FiatCurrency,
  /**
   * The amount of money.
   *
   * Can be fractional beyond what the corresponding [FiatCurrency] supports
   * (i.e. 1.125 is valid for USD). Callers should perform rounding and formatting.
   */
  override val value: BigDecimal,
) : Money {
  /**
   * Creates a [FiatMoney] object by specifying the amount in the fractional unit.
   * i.e. initializing with [USD] and 500 would create an object representing $5.00
   */
  constructor(currency: FiatCurrency, fractionalUnitAmount: BigInteger) : this(
    currency = currency,
    value = currency.unitValueFromFractionalUnitValue(fractionalUnitAmount)
  )

  override fun toString() = "Money(${currency.textCode.code},${value.toPlainString()})"

  operator fun plus(other: FiatMoney): FiatMoney {
    require(this.currency == other.currency)
    return copy(value = this.value + other.value)
  }

  operator fun minus(other: FiatMoney): FiatMoney {
    require(this.currency == other.currency)
    return copy(value = this.value - other.value)
  }

  @Suppress("TooManyFunctions")
  companion object {
    fun zero(currency: FiatCurrency) = FiatMoney(currency = currency, value = BigDecimal.ZERO)

    fun zeroUsd() = zero(USD)

    fun zeroEur() = zero(EUR)

    /** Constructors for creating USD Money given dollar amount. */
    fun usd(dollars: BigDecimal) = FiatMoney(currency = USD, value = dollars)

    fun usd(dollars: Double) = usd(dollars = dollars.toBigDecimal())

    /** Constructors for creating USD Money given cents amount. */
    fun usd(cents: BigInteger) = FiatMoney(currency = USD, fractionalUnitAmount = cents)

    fun usd(cents: Long) = usd(cents = cents.toBigInteger())

    fun usd(cents: Int) = usd(cents = cents.toBigInteger())

    /** Constructors for creating EUR Money given euro amount. */
    fun eur(euros: BigDecimal) = FiatMoney(currency = EUR, value = euros)

    fun eur(euros: Double) = eur(euros = euros.toBigDecimal())

    /** Constructors for creating EUR Money given cents amount. */
    fun eur(cents: BigInteger) = FiatMoney(currency = EUR, fractionalUnitAmount = cents)

    fun eur(cents: Long) = eur(cents = cents.toBigInteger())

    fun eur(cents: Int) = eur(cents = cents.toBigInteger())
  }
}
