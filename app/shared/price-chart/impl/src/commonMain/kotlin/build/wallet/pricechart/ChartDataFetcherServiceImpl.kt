package build.wallet.pricechart

import bitkey.account.AccountConfigService
import build.wallet.account.AccountService
import build.wallet.account.getAccount
import build.wallet.bitkey.account.FullAccount
import build.wallet.di.AppScope
import build.wallet.di.BitkeyInject
import build.wallet.ktor.result.HttpError
import build.wallet.money.display.FiatCurrencyPreferenceRepository
import build.wallet.money.exchange.ExchangeRateF8eClient
import com.github.michaelbull.result.Err
import com.github.michaelbull.result.Result
import com.github.michaelbull.result.getOrElse
import com.github.michaelbull.result.map
import kotlin.collections.map

@BitkeyInject(AppScope::class)
class ChartDataFetcherServiceImpl(
  private val exchangeRateF8eClient: ExchangeRateF8eClient,
  private val fiatCurrencyPreferenceRepository: FiatCurrencyPreferenceRepository,
  private val accountConfigService: AccountConfigService,
  private val accountService: AccountService,
) : ChartDataFetcherService {
  override suspend fun getChartData(
    range: ChartRange,
    maxPricePoints: Int?,
  ): Result<List<DataPoint>, Error> {
    val f8eEnvironment = accountConfigService.activeOrDefaultConfig().value.f8eEnvironment
    val account = accountService.getAccount<FullAccount>()
      .getOrElse { return Err(HttpError.UnhandledException(it)) }
    val currencyCode = fiatCurrencyPreferenceRepository.fiatCurrencyPreference.value.textCode.code
    return exchangeRateF8eClient.getHistoricalBtcExchangeRateChartData(
      f8eEnvironment = f8eEnvironment,
      accountId = account.accountId,
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
