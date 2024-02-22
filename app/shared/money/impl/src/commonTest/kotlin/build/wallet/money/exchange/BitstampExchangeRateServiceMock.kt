package build.wallet.money.exchange

import build.wallet.ktor.result.HttpError
import build.wallet.money.exchange.BitstampExchangeRateService.HistoricalBtcExchangeError
import com.github.michaelbull.result.Ok
import com.github.michaelbull.result.Result
import com.github.michaelbull.result.map
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.datetime.Instant

class BitstampExchangeRateServiceMock : BitstampExchangeRateService {
  val btcToUsdExchangeRate =
    MutableStateFlow<Result<ExchangeRate, HttpError>>(
      Ok(ExchangeRateFake)
    )
  val historicalBtcToUsdExchangeRate =
    MutableStateFlow<Result<ExchangeRate, HistoricalBtcExchangeError>>(
      Ok(ExchangeRateFake)
    )

  override suspend fun getExchangeRates(): Result<List<ExchangeRate>, HttpError> {
    return btcToUsdExchangeRate.value.map { listOf(it) }
  }

  override suspend fun getHistoricalBtcExchangeRates(
    time: Instant,
  ): Result<List<ExchangeRate>, HistoricalBtcExchangeError> {
    return historicalBtcToUsdExchangeRate.value.map { listOf(it) }
  }
}
