package build.wallet.statemachine.partnerships.purchase

import build.wallet.money.BitcoinMoney
import build.wallet.money.BitcoinMoney.Companion.btc
import build.wallet.money.currency.Currency
import build.wallet.money.exchange.CurrencyConverter
import build.wallet.money.exchange.ExchangeRate
import build.wallet.money.formatter.MoneyDisplayFormatter
import build.wallet.partnerships.PurchaseQuote

/**
 * A convenience class which wraps a [PurchaseQuote] and provides displayable versions of its properties.
 * @property quote the [PurchaseQuote] to display
 * @property bitcoinDisplayAmount the amount of bitcoin of the quote
 * @property fiatDisplayAmount the amount of fiat currency of the quote, if available
 */
internal data class PurchaseQuoteModel(
  val quote: PurchaseQuote, // TODO: convert to model fields
  val bitcoinDisplayAmount: String,
  val fiatDisplayAmount: String?,
)

/**
 * A mapping function which converts a [PurchaseQuote] to a [PurchaseQuoteModel].
 */
internal fun PurchaseQuote.toQuoteModel(
  moneyDisplayFormatter: MoneyDisplayFormatter,
  exchangeRates: List<ExchangeRate>,
  currencyConverter: CurrencyConverter,
): PurchaseQuoteModel {
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
  return PurchaseQuoteModel(this, bitcoinDisplayAmount, fiatDisplayAmount)
}

private fun PurchaseQuote.bitcoinAmount(): BitcoinMoney = btc(cryptoAmount)

private fun PurchaseQuote.fiatCurrency(): Currency? {
  return when (fiatCurrency) {
    "USD" -> build.wallet.money.currency.USD
    "EUR" -> build.wallet.money.currency.EUR
    "GBP" -> build.wallet.money.currency.GBP
    else -> null
  }
}
