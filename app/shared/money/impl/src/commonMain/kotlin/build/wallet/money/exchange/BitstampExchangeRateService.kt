package build.wallet.money.exchange

import build.wallet.ktor.result.NetworkingError
import com.github.michaelbull.result.Result
import kotlinx.datetime.Instant

interface BitstampExchangeRateService {
  /**
   * Retrieves exchange rates from www.bitstamp.net.
   * Gets all rates Bitstamp supports and maps it to Currency values we support.
   * Not all Currency values are supported on Bitstamp, so this will end up being a subset.
   */
  suspend fun getExchangeRates(): Result<List<ExchangeRate>, NetworkingError>

  /**
   * Retrieves BTC exchange rates at the given time from www.bitstamp.net.
   */
  suspend fun getHistoricalBtcExchangeRates(
    time: Instant,
  ): Result<List<ExchangeRate>, HistoricalBtcExchangeError>

  /** Possible failure types for historical exchange rates */
  sealed class HistoricalBtcExchangeError : Error() {
    data class Networking(override val cause: Error) : HistoricalBtcExchangeError()

    data object MalformedResponseBody : HistoricalBtcExchangeError()
  }
}
