package build.wallet.pricechart

import bitkey.account.AccountConfigService
import build.wallet.bitkey.f8e.AccountId
import build.wallet.di.AppScope
import build.wallet.di.BitkeyInject
import build.wallet.ktor.result.NetworkingError
import build.wallet.money.display.FiatCurrencyPreferenceRepository
import build.wallet.money.exchange.ExchangeRateF8eClient
import com.github.michaelbull.result.Result
import com.github.michaelbull.result.map
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.withContext

@BitkeyInject(AppScope::class)
class ChartDataFetcherServiceImpl(
  private val exchangeRateF8eClient: ExchangeRateF8eClient,
  private val fiatCurrencyPreferenceRepository: FiatCurrencyPreferenceRepository,
  private val accountConfigService: AccountConfigService,
) : ChartDataFetcherService {
  override suspend fun getChartData(
    accountId: AccountId,
    chartHistory: ChartHistory,
    maxPricePoints: Int?,
  ): Result<List<DataPoint>, NetworkingError> =
    withContext(Dispatchers.IO) {
      val f8eEnvironment = accountConfigService.activeOrDefaultConfig().value.f8eEnvironment
      exchangeRateF8eClient.getHistoricalBtcExchangeRateChartData(
        f8eEnvironment = f8eEnvironment,
        accountId = accountId,
        currencyCode = fiatCurrencyPreferenceRepository.fiatCurrencyPreference.value.textCode.code,
        days = chartHistory.days,
        maxPricePoints = maxPricePoints ?: chartHistory.maxPricePoints
      ).map { chartData ->
        chartData.exchangeRates.map { priceAt ->
          DataPoint(priceAt.timestamp.epochSeconds, priceAt.price)
        }
      }
    }
}
