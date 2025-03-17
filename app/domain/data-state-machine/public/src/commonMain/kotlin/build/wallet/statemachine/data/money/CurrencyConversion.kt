package build.wallet.statemachine.data.money

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.remember
import build.wallet.money.Money
import build.wallet.money.currency.Currency
import build.wallet.money.exchange.CurrencyConverter
import build.wallet.money.exchange.ExchangeRate
import com.ionspin.kotlin.bignum.decimal.toBigDecimal
import kotlinx.collections.immutable.ImmutableList
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.datetime.Instant

/**
 * A Composable function that converts [fromAmount] to [toCurrency] using [converter] and returns
 * the result. Initially returns [Money.zero]. If the conversion fails (converter emits null),
 * emitted value is not changed.
 *
 * This function is particularly useful for use in other Composable functions where the converted
 * amount needs to be persisted across recompositions, and recomposed when parameters change.
 */
@Composable
fun convertedOrZero(
  converter: CurrencyConverter,
  fromAmount: Money,
  toCurrency: Currency,
  atTime: Instant? = null,
): Money {
  val initialValue = remember(toCurrency) { Money.money(toCurrency, 0.toBigDecimal()) }
  return remember(converter, fromAmount, toCurrency, atTime) {
    converter.convert(fromAmount, toCurrency, atTime).filterNotNull()
  }.collectAsState(initialValue).value
}

@Composable
fun convertedOrZeroWithRates(
  converter: CurrencyConverter,
  fromAmount: Money,
  toCurrency: Currency,
  rates: ImmutableList<ExchangeRate>,
): Money {
  val defaultValue = remember(toCurrency) { Money.money(toCurrency, 0.toBigDecimal()) }
  return remember(converter, fromAmount, toCurrency) {
    converter.convert(fromAmount, toCurrency, rates) ?: defaultValue
  }
}

/**
 * A Composable function that converts [fromAmount] to [toCurrency] using [converter] and returns
 * the result. Initially returns `null`. If the conversion fails (converter emits null), the `null`
 * value is emitted, which puts responsibility on the caller to handle the null case.
 *
 * This function is particularly useful for use in other Composable functions where the converted
 * amount needs to be persisted across recompositions, and recomposed when parameters change.
 */
@Composable
fun convertedOrNull(
  converter: CurrencyConverter,
  fromAmount: Money,
  toCurrency: Currency,
  atTime: Instant? = null,
): Money? {
  return remember(converter, fromAmount, toCurrency, atTime) {
    converter.convert(fromAmount, toCurrency, atTime)
  }.collectAsState(null).value
}
