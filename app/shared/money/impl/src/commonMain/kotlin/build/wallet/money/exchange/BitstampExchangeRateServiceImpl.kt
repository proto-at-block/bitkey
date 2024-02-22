package build.wallet.money.exchange

import build.wallet.ktor.result.NetworkingError
import build.wallet.ktor.result.bodyResult
import build.wallet.logging.LogLevel.Warn
import build.wallet.logging.log
import build.wallet.logging.logNetworkFailure
import build.wallet.money.currency.code.IsoCurrencyTextCode
import build.wallet.money.exchange.BitstampExchangeRateService.HistoricalBtcExchangeError
import build.wallet.money.exchange.BitstampExchangeRateService.HistoricalBtcExchangeError.MalformedResponseBody
import build.wallet.money.exchange.BitstampExchangeRateService.HistoricalBtcExchangeError.Networking
import com.github.michaelbull.result.Err
import com.github.michaelbull.result.Ok
import com.github.michaelbull.result.Result
import com.github.michaelbull.result.get
import com.github.michaelbull.result.map
import com.github.michaelbull.result.mapBoth
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.datetime.Instant
import kotlinx.serialization.Serializable

class BitstampExchangeRateServiceImpl(
  private val bitstampHttpClient: BitstampHttpClient,
) : BitstampExchangeRateService {
  override suspend fun getExchangeRates(): Result<List<ExchangeRate>, NetworkingError> {
    return bitstampHttpClient.client()
      .bodyResult<List<BitstampExchangeRate>> { get("/api/v2/ticker/") }
      .map { exchangeRates ->
        exchangeRates.mapNotNull { body ->
          body.currencyPair()
            ?.exchangeRate(
              rate = body.ask,
              timeRetrieved = Instant.fromEpochSeconds(body.timestamp.toLong())
            )
        }
      }
      .logNetworkFailure { "Failed to get exchange rates" }
  }

  override suspend fun getHistoricalBtcExchangeRates(
    time: Instant,
  ): Result<List<ExchangeRate>, HistoricalBtcExchangeError> {
    // Launch separate coroutine jobs for each request so they happen concurrently
    val supportedHistoricalFiatCurrencies = listOf("USD", "GBP", "EUR").map(::IsoCurrencyTextCode)
    val deferredRates =
      coroutineScope {
        supportedHistoricalFiatCurrencies.map { fiatCurrencyCode ->
          async {
            getHistoricalExchangeRate(
              fiatCurrencyCode = fiatCurrencyCode,
              time = time
            )
          }
        }
      }
    val rates = deferredRates.awaitAll().mapNotNull { it.get() }
    // We explicitly return `Ok` here because we expect some lookups to fail with 404 not found
    // because only a subset of currencies are supported by Bitstamp. Failures will be logged.
    // This is all temporary and will be replaced by calls to f8e.
    return Ok(rates)
  }

  private suspend fun getHistoricalExchangeRate(
    fiatCurrencyCode: IsoCurrencyTextCode,
    time: Instant,
  ): Result<ExchangeRate, HistoricalBtcExchangeError> {
    val currencyExchangeRatePair =
      CurrencyExchangeRatePair(
        fromCurrency = IsoCurrencyTextCode("BTC"),
        toCurrency = fiatCurrencyCode
      )
    return bitstampHttpClient.client()
      .bodyResult<BitstampHistoricalExchangeRate> {
        get("/api/v2/ohlc/${currencyExchangeRatePair.ohlcEndpointPath}/") {
          // Set step - Timeframe in seconds. Possible options are 60, 180, 300, 900, 1800, 3600, 7200, 14400, 21600, 43200, 86400, 259200
          parameter("step", 60)
          // Set limit - Limit OHLC results (minimum: 1; maximum: 1000)
          parameter("limit", 1)
          // Set start – Unix timestamp from when OHLC data will be started.
          parameter("start", time.epochSeconds)
          // Set end – Unix timestamp to when OHLC data will be shown.
          parameter("end", time.epochSeconds)
        }
      }
      .logNetworkFailure(Warn) {
        "Failed to get historical ${currencyExchangeRatePair.ohlcEndpointPath} exchange rate"
      }
      .mapBoth(
        success = { body ->
          val ohlc = body.data.ohlc.firstOrNull()
          if (ohlc == null) {
            log(
              Warn
            ) { "Received empty ohlc list in historical exchange rate response body: $body" }
            Err(MalformedResponseBody)
          } else {
            Ok(currencyExchangeRatePair.exchangeRate(rate = ohlc.close, timeRetrieved = time))
          }
        },
        failure = { Err(Networking(it)) }
      )
  }

  /** Represents a singular currency exchange rate from www.bitstamp.net */
  @Serializable
  private data class BitstampExchangeRate(
    /** The ask price for the exchange. */
    val ask: Double,
    val pair: String,
    val timestamp: String,
  ) {
    val codes = pair.split('/')

    // Currency pair for the exchange rate, if they are both currencies we have definitions for
    fun currencyPair(): CurrencyExchangeRatePair? {
      return CurrencyExchangeRatePair(
        fromCurrency = codes.elementAtOrNull(0)?.let { IsoCurrencyTextCode(it) } ?: return null,
        toCurrency = codes.elementAtOrNull(1)?.let { IsoCurrencyTextCode(it) } ?: return null
      )
    }
  }

  /**
   * Represents a singular historical currency exchange rate from www.bitstamp.net
   */
  @Serializable
  private data class BitstampHistoricalExchangeRate(
    val data: Data,
  ) {
    @Serializable
    data class Data(
      val ohlc: List<OHLC>,
    )

    /**
     * Represents a singular currency exchange rate (Open High Low Close).
     * More information is provided by the endpoint, but we only care about the close price.
     */
    @Serializable
    data class OHLC(
      /** The close price for the exchange. */
      val close: Double,
    )
  }
}

private data class CurrencyExchangeRatePair(
  val fromCurrency: IsoCurrencyTextCode,
  val toCurrency: IsoCurrencyTextCode,
) {
  // For the Bitstamp OHLC endpoint, the 2 currencies need to be lowercase and combined
  // with no delimiters to form the API path
  val ohlcEndpointPath =
    fromCurrency.code.lowercase() +
      toCurrency.code.lowercase()

  // Form an [ExchangeRate] object for the pair with the given rate
  fun exchangeRate(
    rate: Double,
    timeRetrieved: Instant,
  ) = ExchangeRate(
    fromCurrency = fromCurrency,
    toCurrency = toCurrency,
    rate = rate,
    timeRetrieved = timeRetrieved
  )
}
