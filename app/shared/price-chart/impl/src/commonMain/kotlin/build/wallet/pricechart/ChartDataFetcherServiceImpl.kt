package build.wallet.pricechart

import build.wallet.bitkey.f8e.FullAccountId
import build.wallet.f8e.F8eEnvironment
import build.wallet.ktor.result.NetworkingError
import build.wallet.money.display.FiatCurrencyPreferenceRepository
import build.wallet.money.exchange.ExchangeRateF8eClient
import com.github.michaelbull.result.Result
import com.github.michaelbull.result.map
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.withContext

class ChartDataFetcherServiceImpl(
  private val exchangeRateF8eClient: ExchangeRateF8eClient,
  private val fiatCurrencyPreferenceRepository: FiatCurrencyPreferenceRepository,
) : ChartDataFetcherService {
  override suspend fun getChartData(
    fullAccountId: FullAccountId,
    f8eEnvironment: F8eEnvironment,
    chartHistory: ChartHistory,
    maxPricePoints: Int?,
  ): Result<List<DataPoint>, NetworkingError> =
    withContext(Dispatchers.IO) {
      exchangeRateF8eClient.getHistoricalBtcExchangeRateChartData(
        f8eEnvironment,
        fullAccountId,
        fiatCurrencyPreferenceRepository.fiatCurrencyPreference.value.textCode.code,
        chartHistory.days,
        maxPricePoints ?: chartHistory.maxPricePoints
      ).map { chartData ->
        chartData.exchangeRates.map { priceAt ->
          DataPoint(priceAt.timestamp.epochSeconds, priceAt.price)
        }
      }
    }
}
