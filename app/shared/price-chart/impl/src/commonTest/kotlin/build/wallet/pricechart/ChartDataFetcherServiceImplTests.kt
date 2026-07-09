package build.wallet.pricechart

import build.wallet.account.AccountServiceFake
import build.wallet.activity.Transaction
import build.wallet.balance.utils.DataQuality
import build.wallet.balance.utils.MockConfiguration
import build.wallet.balance.utils.MockDataPoint
import build.wallet.balance.utils.MockPriceScenario
import build.wallet.balance.utils.MockScenarioService
import build.wallet.balance.utils.MockTransactionScenario
import build.wallet.bitkey.f8e.AccountId
import build.wallet.bitkey.keybox.FullAccountMock
import build.wallet.ktor.result.NetworkingError
import build.wallet.money.currency.FiatCurrency
import build.wallet.money.currency.USD
import build.wallet.money.display.FiatCurrencyPreferenceRepository
import build.wallet.money.exchange.ExchangeRate
import build.wallet.money.exchange.ExchangeRateChartData
import build.wallet.money.exchange.ExchangeRateF8eClient
import build.wallet.money.exchange.PriceAt
import com.github.michaelbull.result.Ok
import com.github.michaelbull.result.Result
import build.wallet.testing.shouldBeOk
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.datetime.Instant
import kotlin.time.Duration

class ChartDataFetcherServiceImplTests : FunSpec({
  test("uses the provided account id and environment without waiting for an active account") {
    val exchangeRateF8eClient = FakeExchangeRateF8eClient()
    val service = ChartDataFetcherServiceImpl(
      exchangeRateF8eClient = exchangeRateF8eClient,
      fiatCurrencyPreferenceRepository = TestFiatCurrencyPreferenceRepository(),
      accountService = AccountServiceFake(),
      mockScenarioService = NoopMockScenarioService()
    )

    service.getChartData(
      range = ChartRange.DAY,
      accountId = FullAccountMock.accountId,
      f8eEnvironment = FullAccountMock.config.f8eEnvironment,
    ).shouldBeOk()
      .map { dataPoint -> dataPoint.x to dataPoint.y }
      .shouldBe(
        exchangeRateF8eClient.chartData.exchangeRates.map { priceAt ->
          priceAt.timestamp.epochSeconds to priceAt.price
        }
      )

    exchangeRateF8eClient.requestedAccountIds.shouldBe(listOf(FullAccountMock.accountId))
    exchangeRateF8eClient.requestedF8eEnvironments.shouldBe(listOf(FullAccountMock.config.f8eEnvironment))
  }

  test("falls back to the active full account when a consistent fast path is unavailable") {
    val exchangeRateF8eClient = FakeExchangeRateF8eClient()
    val accountService = AccountServiceFake()
    accountService.setActiveAccount(FullAccountMock)

    val service = ChartDataFetcherServiceImpl(
      exchangeRateF8eClient = exchangeRateF8eClient,
      fiatCurrencyPreferenceRepository = TestFiatCurrencyPreferenceRepository(),
      accountService = accountService,
      mockScenarioService = NoopMockScenarioService()
    )

    service.getChartData(
      range = ChartRange.DAY,
      accountId = FullAccountMock.accountId,
    ).shouldBeOk()

    exchangeRateF8eClient.requestedAccountIds.shouldBe(listOf(FullAccountMock.accountId))
    exchangeRateF8eClient.requestedF8eEnvironments.shouldBe(listOf(FullAccountMock.config.f8eEnvironment))
  }
})

internal class TestFiatCurrencyPreferenceRepository : FiatCurrencyPreferenceRepository {
  override val fiatCurrencyPreference = MutableStateFlow(USD)

  override suspend fun setFiatCurrencyPreference(fiatCurrency: FiatCurrency): Result<Unit, Error> {
    fiatCurrencyPreference.value = fiatCurrency
    return Ok(Unit)
  }

  override suspend fun clear(): Result<Unit, Error> = Ok(Unit)
}

private class FakeExchangeRateF8eClient : ExchangeRateF8eClient {
  val requestedAccountIds = mutableListOf<AccountId>()
  val requestedF8eEnvironments = mutableListOf<build.wallet.f8e.F8eEnvironment>()
  val chartData = ExchangeRateChartData(
    fromCurrency = USD.textCode,
    toCurrency = USD.textCode,
    exchangeRates = listOf(
      PriceAt(price = 65000.0, timestamp = Instant.fromEpochSeconds(1)),
      PriceAt(price = 66000.0, timestamp = Instant.fromEpochSeconds(2))
    )
  )

  override suspend fun getExchangeRates(
    f8eEnvironment: build.wallet.f8e.F8eEnvironment,
  ): Result<List<ExchangeRate>, NetworkingError> {
    error("Unused in test")
  }

  override suspend fun getHistoricalBtcExchangeRates(
    f8eEnvironment: build.wallet.f8e.F8eEnvironment,
    accountId: AccountId,
    currencyCode: String,
    timestamps: List<Instant>,
  ): Result<List<ExchangeRate>, NetworkingError> {
    error("Unused in test")
  }

  override suspend fun getHistoricalBtcExchangeRateChartData(
    f8eEnvironment: build.wallet.f8e.F8eEnvironment,
    accountId: AccountId,
    currencyCode: String,
    days: Duration,
    maxPricePoints: Int,
  ): Result<ExchangeRateChartData, NetworkingError> {
    requestedAccountIds += accountId
    requestedF8eEnvironments += f8eEnvironment
    return Ok(chartData)
  }
}

private class NoopMockScenarioService : MockScenarioService {
  override suspend fun currentMockConfiguration(): MockConfiguration? = null

  override suspend fun currentPriceScenario(): MockPriceScenario? = null

  override suspend fun currentTransactionScenario(): MockTransactionScenario? = null

  override suspend fun currentDataQuality(): DataQuality? = null

  override suspend fun currentSeed(): Long? = null

  override suspend fun generateTransactions(): List<Transaction> = emptyList()

  override suspend fun generatePriceData(
    maxPoints: Int,
    fiatCurrency: FiatCurrency,
    timeRange: Duration,
  ): List<MockDataPoint> = emptyList()

  override fun currentTransactionScenarioFlow(): Flow<MockTransactionScenario?> = flowOf(null)

  override fun currentPriceScenarioFlow(): Flow<MockPriceScenario?> = flowOf(null)

  override fun currentSeedFlow(): Flow<Long?> = flowOf(null)

  override suspend fun setPriceScenario(scenario: MockPriceScenario) {
    // No-op test double.
  }

  override suspend fun setTransactionScenario(scenario: MockTransactionScenario) {
    // No-op test double.
  }

  override suspend fun setDataQuality(dataQuality: DataQuality) {
    // No-op test double.
  }

  override suspend fun setConfiguration(
    config: MockConfiguration,
    generateNewSeed: Boolean,
  ) {
    // No-op test double.
  }

  override suspend fun rotateSeed() {
    // No-op test double.
  }

  override suspend fun clearScenarios(
    clearPrice: Boolean,
    clearTransaction: Boolean,
  ) {
    // No-op test double.
  }
}
