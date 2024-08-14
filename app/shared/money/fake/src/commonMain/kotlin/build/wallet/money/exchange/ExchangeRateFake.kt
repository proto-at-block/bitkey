package build.wallet.money.exchange

import build.wallet.money.currency.code.IsoCurrencyTextCode
import kotlinx.datetime.Instant

val ExchangeRateFake = USDtoBTC(0.5)

fun USDtoBTC(
  rate: Double,
  seconds: Long = 100,
) = ExchangeRate(
  fromCurrency = IsoCurrencyTextCode("USD"),
  toCurrency = IsoCurrencyTextCode("BTC"),
  rate = rate,
  timeRetrieved = Instant.fromEpochSeconds(seconds)
)

fun EURtoBTC(rate: Double) =
  ExchangeRate(
    fromCurrency = IsoCurrencyTextCode("EUR"),
    toCurrency = IsoCurrencyTextCode("BTC"),
    rate = rate,
    timeRetrieved = Instant.fromEpochSeconds(200)
  )
