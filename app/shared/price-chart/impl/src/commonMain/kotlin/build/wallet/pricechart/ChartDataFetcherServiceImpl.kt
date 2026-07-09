package build.wallet.pricechart

import build.wallet.account.AccountService
import build.wallet.account.getAccount
import build.wallet.balance.utils.MockScenarioService
import build.wallet.bitkey.account.FullAccount
import build.wallet.bitkey.f8e.AccountId
import build.wallet.di.AppScope
import build.wallet.di.BitkeyInject
import build.wallet.f8e.F8eEnvironment
import build.wallet.ktor.result.HttpError
import com.github.michaelbull.result.Err
import build.wallet.money.display.FiatCurrencyPreferenceRepository
import build.wallet.money.exchange.ExchangeRateF8eClient
import com.github.michaelbull.result.Ok
import com.github.michaelbull.result.Result
import com.github.michaelbull.result.getOrElse
import com.github.michaelbull.result.map
import kotlin.collections.map

@BitkeyInject(AppScope::class)
class ChartDataFetcherServiceImpl(
  private val exchangeRateF8eClient: ExchangeRateF8eClient,
  private val fiatCurrencyPreferenceRepository: FiatCurrencyPreferenceRepository,
  private val accountService: AccountService,
  private val mockScenarioService: MockScenarioService,
) : ChartDataFetcherService {
  override suspend fun getChartData(
    range: ChartRange,
    maxPricePoints: Int?,
    accountId: AccountId?,
    f8eEnvironment: F8eEnvironment?,
  ): Result<List<DataPoint>, Error> {
    // Use mock data if available (Not available in customer builds)
    mockScenarioService.currentPriceScenario()?.let { _ ->
      val fiatCurrency = fiatCurrencyPreferenceRepository.fiatCurrencyPreference.value
      val maxPoints = maxPricePoints ?: range.maxPricePoints
      val mockPriceData = mockScenarioService.generatePriceData(maxPoints, fiatCurrency, range.duration)
      // Convert MockDataPoint to DataPoint
      val dataPoints = mockPriceData.map { DataPoint(it.timestamp, it.price) }
      return Ok(dataPoints)
    }

    val (resolvedAccountId, resolvedF8eEnvironment) =
      if (accountId != null && f8eEnvironment != null) {
        // Callers that already have account context can fetch without waiting on active account resolution.
        accountId to f8eEnvironment
      } else {
        val account = accountService.getAccount<FullAccount>()
          .getOrElse { return Err(HttpError.UnhandledException(it)) }
        account.accountId to account.config.f8eEnvironment
      }
    val currencyCode = fiatCurrencyPreferenceRepository.fiatCurrencyPreference.value.textCode.code
    return exchangeRateF8eClient.getHistoricalBtcExchangeRateChartData(
      f8eEnvironment = resolvedF8eEnvironment,
      accountId = resolvedAccountId,
      currencyCode = currencyCode,
      days = range.duration,
      maxPricePoints = maxPricePoints ?: range.maxPricePoints
    ).map { chartData ->
      chartData.exchangeRates.map { priceAt ->
        DataPoint(priceAt.timestamp.epochSeconds, priceAt.price)
      }
    }
  }
}
