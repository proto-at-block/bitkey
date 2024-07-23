package build.wallet.statemachine.partnerships.purchase

import build.wallet.f8e.partnerships.Quote
import build.wallet.f8e.partnerships.bitcoinAmount
import build.wallet.f8e.partnerships.fiatCurrency
import build.wallet.money.exchange.CurrencyConverter
import build.wallet.money.exchange.ExchangeRate
import build.wallet.money.formatter.MoneyDisplayFormatter

/**
 * A convenience class which wraps a [Quote] and provides displayable versions of its properties.
 * @property quote the [Quote] to display
 * @property bitcoinDisplayAmount the amount of bitcoin of the quote
 * @property fiatDisplayAmount the amount of fiat currency of the quote, if available
 */
data class QuoteDisplay(
  val quote: Quote,
  val bitcoinDisplayAmount: String,
  val fiatDisplayAmount: String?,
)

/**
 * A mapping function which converts a [Quote] to a [QuoteDisplay].
 */
fun Quote.toQuoteDisplay(
  moneyDisplayFormatter: MoneyDisplayFormatter,
  exchangeRates: List<ExchangeRate>,
  currencyConverter: CurrencyConverter,
): QuoteDisplay {
  val bitcoinDisplayAmount = moneyDisplayFormatter.format(bitcoinAmount())
  val fiatDisplayAmount = fiatCurrency()?.let {
    currencyConverter.convert(
      fromAmount = bitcoinAmount(),
      toCurrency = it,
      rates = exchangeRates
    )
  }?.let { fiatAmount ->
    moneyDisplayFormatter.format(fiatAmount)
  }
  return QuoteDisplay(this, bitcoinDisplayAmount, fiatDisplayAmount)
}
