package build.wallet.money.exchange

import build.wallet.bitkey.f8e.AccountId
import build.wallet.f8e.F8eEnvironment
import build.wallet.ktor.result.HttpError
import build.wallet.ktor.result.NetworkingError
import com.github.michaelbull.result.Ok
import com.github.michaelbull.result.Result
import com.github.michaelbull.result.map
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.datetime.Instant

class ExchangeRateF8eClientMock : ExchangeRateF8eClient {
  val exchangeRates =
    MutableStateFlow<Result<List<ExchangeRate>, HttpError>>(
      Ok(listOf(ExchangeRateFake))
    )
  val historicalBtcToUsdExchangeRate =
    MutableStateFlow<Result<ExchangeRate, NetworkingError>>(
      Ok(ExchangeRateFake)
    )

  override suspend fun getExchangeRates(
    f8eEnvironment: F8eEnvironment,
  ): Result<List<ExchangeRate>, NetworkingError> {
    return exchangeRates.value
  }

  override suspend fun getHistoricalBtcExchangeRates(
    f8eEnvironment: F8eEnvironment,
    accountId: AccountId,
    currencyCode: String,
    timestamps: List<Instant>,
  ): Result<List<ExchangeRate>, NetworkingError> {
    return historicalBtcToUsdExchangeRate.value.map { listOf(it) }
  }
}
