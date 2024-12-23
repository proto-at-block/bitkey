package build.wallet.money.exchange

import build.wallet.bitkey.f8e.AccountId
import build.wallet.di.AppScope
import build.wallet.di.BitkeyInject
import build.wallet.f8e.F8eEnvironment
import build.wallet.f8e.client.F8eHttpClient
import build.wallet.f8e.client.plugins.withEnvironment
import build.wallet.ktor.result.NetworkingError
import build.wallet.ktor.result.RedactedRequestBody
import build.wallet.ktor.result.RedactedResponseBody
import build.wallet.ktor.result.bodyResult
import build.wallet.ktor.result.setRedactedBody
import build.wallet.logging.logNetworkFailure
import build.wallet.money.currency.code.IsoCurrencyTextCode
import com.github.michaelbull.result.Result
import com.github.michaelbull.result.map
import dev.zacsweers.redacted.annotations.Unredacted
import io.ktor.client.request.get
import io.ktor.client.request.post
import kotlinx.datetime.Instant
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlin.time.Duration
import kotlin.time.DurationUnit

@BitkeyInject(AppScope::class)
class ExchangeRateF8eClientImpl(
  private val f8eHttpClient: F8eHttpClient,
) : ExchangeRateF8eClient {
  override suspend fun getExchangeRates(
    f8eEnvironment: F8eEnvironment,
  ): Result<List<ExchangeRate>, NetworkingError> {
    return f8eHttpClient.unauthenticated()
      .bodyResult<ResponseBody> {
        get("/api/exchange-rates") {
          withEnvironment(f8eEnvironment)
        }
      }.map { response ->
        response.exchangeRates.map { body ->
          body.exchangeRate()
        }
      }
      .logNetworkFailure { "Failed to get exchange rates" }
  }

  override suspend fun getHistoricalBtcExchangeRates(
    f8eEnvironment: F8eEnvironment,
    accountId: AccountId,
    currencyCode: String,
    timestamps: List<Instant>,
  ): Result<List<ExchangeRate>, NetworkingError> {
    return f8eHttpClient.authenticated(f8eEnvironment, accountId)
      .bodyResult<ResponseBody> {
        post("/api/exchange-rates/historical") {
          setRedactedBody(HistoricalRateRequest(currencyCode, timestamps.map { it.epochSeconds }))
        }
      }
      .map { response ->
        response.exchangeRates.map { body ->
          body.exchangeRate()
        }
      }
      .logNetworkFailure { "Failed to get historical exchange rate for $currencyCode" }
  }

  override suspend fun getHistoricalBtcExchangeRateChartData(
    f8eEnvironment: F8eEnvironment,
    accountId: AccountId,
    currencyCode: String,
    days: Duration,
    maxPricePoints: Int,
  ): Result<ExchangeRateChartData, NetworkingError> =
    f8eHttpClient.authenticated(f8eEnvironment, accountId)
      .bodyResult<ExchangeRateChartDataDTO> {
        post("/api/exchange-rates/chart") {
          setRedactedBody(
            HistoricalBtcExchangeRateChartData(
              currencyCode,
              days.toInt(DurationUnit.DAYS),
              maxPricePoints
            )
          )
        }
      }
      .map { it.toExchangeRateChartData() }
      .logNetworkFailure { "Failed to get historical exchange rate chart data for $currencyCode" }

  @Serializable
  data class HistoricalRateRequest(
    @SerialName("currency_code")
    val currencyCode: String,
    val timestamps: List<Long>,
  ) : RedactedRequestBody

  @Serializable
  private data class ResponseBody(
    @Unredacted
    @SerialName("exchange_rates")
    val exchangeRates: List<ExchangeRateDTO>,
  ) : RedactedResponseBody

  @Serializable
  private data class ExchangeRateDTO(
    @SerialName("from_currency")
    val fromCurrency: String,
    @SerialName("to_currency")
    val toCurrency: String,
    @SerialName("time_retrieved")
    val timeRetrieved: Instant,
    val rate: Double,
  ) {
    fun exchangeRate(): ExchangeRate {
      return ExchangeRate(
        fromCurrency = IsoCurrencyTextCode(fromCurrency),
        toCurrency = IsoCurrencyTextCode(toCurrency),
        rate = rate,
        timeRetrieved = timeRetrieved
      )
    }
  }

  @Serializable
  data class HistoricalBtcExchangeRateChartData(
    @SerialName("currency_code")
    val currencyCode: String,
    val days: Int,
    @SerialName("max_price_points")
    val maxPricePoints: Int,
  ) : RedactedRequestBody

  @Serializable
  private data class ExchangeRateChartDataDTO(
    @SerialName("from_currency")
    val fromCurrency: String,
    @SerialName("to_currency")
    val toCurrency: String,
    @Unredacted
    @SerialName("exchange_rates")
    val exchangeRates: List<PriceAtDTO>,
  ) : RedactedResponseBody {
    fun toExchangeRateChartData(): ExchangeRateChartData {
      return ExchangeRateChartData(
        fromCurrency = IsoCurrencyTextCode(fromCurrency),
        toCurrency = IsoCurrencyTextCode(toCurrency),
        exchangeRates = exchangeRates.map { it.toPriceAt() }
      )
    }
  }

  @Serializable
  private data class PriceAtDTO(
    val timestamp: Instant,
    val price: Double,
  ) {
    fun toPriceAt(): PriceAt {
      return PriceAt(
        timestamp = timestamp,
        price = price
      )
    }
  }
}
