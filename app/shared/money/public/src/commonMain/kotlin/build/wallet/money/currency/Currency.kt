package build.wallet.money.currency

import build.wallet.money.currency.code.IsoCurrencyTextCode
import com.ionspin.kotlin.bignum.decimal.BigDecimal
import com.ionspin.kotlin.bignum.decimal.DecimalMode
import com.ionspin.kotlin.bignum.decimal.RoundingMode
import com.ionspin.kotlin.bignum.decimal.toBigDecimal
import com.ionspin.kotlin.bignum.integer.BigInteger
import kotlin.math.pow

sealed interface Currency {
  /** Alpha code, as defined by ISO 4217. */
  val textCode: IsoCurrencyTextCode

  /**
   *  The number of digits of this fractional unit per the currency's main unit.
   *  0 for currencies that don't use fractional units.
   *
   *  e.g. digits: 2   =>   100 fractional units per unit (like for USD)
   *  e.g. digits: 8   =>   100,000,000 fractional units per unit (like for BTC)
   */
  val fractionalDigits: Int

  /**
   * Main currency unit symbol, e.g. "$", "£", "€" or "₿"
   * If null, text code will be used to display amounts.
   */
  val unitSymbol: String?

  /**
   * The decimal mode to use when rounding amounts in this currency.
   * Arbitrarily chose really large precision like [DecimalMode.US_CURRENCY]
   */
  fun decimalMode(): DecimalMode {
    return DecimalMode(
      decimalPrecision = 30,
      roundingMode = RoundingMode.ROUND_HALF_AWAY_FROM_ZERO,
      scale = fractionalDigits.toLong()
    )
  }

  /**
   * Returns the monetary amount represented in the fractional unit from the main unit.
   * If the currency doesn't support fractional units, just returns the value.
   * i.e. $1.01 => 101, $0.75 => 75
   */
  fun fractionalUnitValueFromUnitValue(value: BigDecimal): BigInteger {
    if (fractionalDigits == 0) {
      // This currency doesn't have fractional units. Return the main unit amount.
      return value.toBigInteger()
    }
    val multiple = 10.0.pow(fractionalDigits).toBigDecimal()
    return value.multiply(multiple).toBigInteger()
  }

  /**
   * Returns the monetary amount represented in the main unit from the fractional unit.
   * If the currency doesn't support fractional units, just returns the value.
   * i.e. 101 => $1.01, 75 => $75
   */
  fun unitValueFromFractionalUnitValue(value: BigInteger): BigDecimal {
    val decimalValue = BigDecimal.fromBigInteger(value)
    if (fractionalDigits == 0) {
      // This currency doesn't have fractional units. Return the main unit amount.
      return decimalValue
    }
    val multiple = 10.0.pow(fractionalDigits).toBigDecimal()
    return decimalValue.divide(
      other = multiple,
      decimalMode = decimalMode()
    )
  }
}
