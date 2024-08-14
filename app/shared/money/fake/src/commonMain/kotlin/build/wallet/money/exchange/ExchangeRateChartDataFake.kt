package build.wallet.money.exchange

import build.wallet.money.currency.code.IsoCurrencyTextCode
import kotlinx.datetime.Instant

val ExchangeRateChartDataFake = ExchangeRateChartData(
  fromCurrency = IsoCurrencyTextCode("USD"),
  toCurrency = IsoCurrencyTextCode("BTC"),
  exchangeRates = listOf(
    PriceAt(70000.0, Instant.fromEpochSeconds(1721759221)),
    PriceAt(60000.0, Instant.fromEpochSeconds(1721758221)),
    PriceAt(50000.0, Instant.fromEpochSeconds(1721757221)),
    PriceAt(40000.0, Instant.fromEpochSeconds(1721756221)),
    PriceAt(30000.0, Instant.fromEpochSeconds(1721755221)),
    PriceAt(40000.0, Instant.fromEpochSeconds(1721754221)),
    PriceAt(50000.0, Instant.fromEpochSeconds(1721753221)),
    PriceAt(60000.0, Instant.fromEpochSeconds(1721752221))
  )
)
