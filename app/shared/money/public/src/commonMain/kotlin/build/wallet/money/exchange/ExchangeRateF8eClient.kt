package build.wallet.money.exchange

import build.wallet.bitkey.f8e.AccountId
import build.wallet.f8e.F8eEnvironment
import build.wallet.ktor.result.NetworkingError
import com.github.michaelbull.result.Result
import kotlinx.datetime.Instant

interface ExchangeRateF8eClient {
  /**
   * Retrieves exchange rates from F8e.
   * Gets all rates F8e supports and maps it to Currency values we support.
   */
  suspend fun getExchangeRates(
    f8eEnvironment: F8eEnvironment,
  ): Result<List<ExchangeRate>, NetworkingError>

  /**
   * Retrieves BTC exchange rates at the given time from F8e.
   */
  suspend fun getHistoricalBtcExchangeRates(
    f8eEnvironment: F8eEnvironment,
    accountId: AccountId,
    currencyCode: String,
    timestamps: List<Instant>,
  ): Result<List<ExchangeRate>, NetworkingError>
}
