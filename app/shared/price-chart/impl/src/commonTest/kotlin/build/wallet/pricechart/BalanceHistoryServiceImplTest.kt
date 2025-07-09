package build.wallet.pricechart

import build.wallet.activity.Transaction
import build.wallet.activity.TransactionsActivityService
import build.wallet.bitcoin.BlockTime
import build.wallet.bitcoin.address.BitcoinAddress
import build.wallet.bitcoin.transactions.BitcoinTransaction
import build.wallet.bitcoin.transactions.BitcoinTransaction.ConfirmationStatus.Confirmed
import build.wallet.bitcoin.transactions.BitcoinTransaction.ConfirmationStatus.Pending
import build.wallet.bitcoin.transactions.BitcoinTransaction.TransactionType.Incoming
import build.wallet.bitcoin.transactions.BitcoinTransactionSendAmount
import build.wallet.bitcoin.transactions.BitcoinWalletService
import build.wallet.bitcoin.transactions.EstimatedTransactionPriority
import build.wallet.bitcoin.transactions.Psbt
import build.wallet.compose.collections.emptyImmutableList
import build.wallet.database.BitkeyDatabaseProviderImpl
import build.wallet.money.BitcoinMoney
import build.wallet.money.Money
import build.wallet.money.currency.Currency
import build.wallet.money.currency.FiatCurrency
import build.wallet.money.currency.code.IsoCurrencyTextCode
import build.wallet.money.display.FiatCurrencyPreferenceRepository
import build.wallet.money.exchange.CurrencyConverter
import build.wallet.money.exchange.ExchangeRate
import build.wallet.pricechart.ChartRange.DAY
import build.wallet.sqldelight.inMemorySqlDriver
import build.wallet.time.ClockFake
import com.github.michaelbull.result.Ok
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.TestScope
import kotlinx.datetime.Instant
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes

/**
 * Tests for BalanceHistoryServiceImpl cache invalidation logic.
 */
class BalanceHistoryServiceImplTest : FunSpec({

  val baseTime = Instant.parse("2025-06-13T12:00:00Z")
  val clock = ClockFake().apply { now = baseTime }
  val testScope = TestScope()
  val databaseProvider = BitkeyDatabaseProviderImpl(inMemorySqlDriver().factory, testScope)

  // Create service with minimal dependencies for testing
  val service = BalanceHistoryServiceImpl(
    databaseProvider = databaseProvider,
    currencyConverter = FakeCurrencyConverter(),
    fiatCurrencyPreferenceRepository = FakeFiatCurrencyPreferenceRepository(),
    chartDataFetcherService = FakeChartDataFetcherService(),
    bitcoinWalletService = FakeBitcoinWalletService(),
    transactionsActivityService = FakeTransactionsActivityService(),
    clock = clock,
    appScope = testScope
  )

  beforeEach {
    databaseProvider.database().walletBalanceQueries.clearAll()
  }

  test("findTransactionsConfirmedAfterCache - transaction confirmed after cache") {
    val cachedAt = baseTime - 1.hours
    val confirmationTime = baseTime - 30.minutes // After cachedAt

    val transaction = createTransaction(confirmationTime = confirmationTime)
    val result = service.findTransactionsConfirmedAfterCache(
      confirmedTxs = listOf(transaction),
      cachedAt = cachedAt,
      rangeStart = baseTime - DAY.duration,
      rangeEnd = baseTime
    )

    result.shouldHaveSize(1)
    result.first().shouldBe(confirmationTime)
  }

  test("findTransactionsConfirmedAfterCache - transaction confirmed before cache") {
    val cachedAt = baseTime - 1.hours
    val confirmationTime = baseTime - 2.hours // Before cachedAt

    val transaction = createTransaction(confirmationTime = confirmationTime)
    val result = service.findTransactionsConfirmedAfterCache(
      confirmedTxs = listOf(transaction),
      cachedAt = cachedAt,
      rangeStart = baseTime - DAY.duration,
      rangeEnd = baseTime
    )

    result.shouldBeEmpty()
  }

  test("findTransactionsConfirmedAfterCache - transaction outside range") {
    val cachedAt = baseTime - 1.hours
    val confirmationTime = baseTime + 1.hours // Outside range

    val transaction = createTransaction(confirmationTime = confirmationTime)
    val result = service.findTransactionsConfirmedAfterCache(
      confirmedTxs = listOf(transaction),
      cachedAt = cachedAt,
      rangeStart = baseTime - DAY.duration,
      rangeEnd = baseTime
    )

    result.shouldBeEmpty()
  }

  test("findTransactionsConfirmedAfterCache - pending transaction") {
    val cachedAt = baseTime - 1.hours
    val transaction = createTransaction(confirmationTime = null) // Pending

    val result = service.findTransactionsConfirmedAfterCache(
      confirmedTxs = listOf(transaction),
      cachedAt = cachedAt,
      rangeStart = baseTime - DAY.duration,
      rangeEnd = baseTime
    )

    result.shouldBeEmpty()
  }

  test("findTransactionsConfirmedAfterCache - mixed transactions") {
    val cachedAt = baseTime - 1.hours

    val transactions = listOf(
      createTransaction(confirmationTime = baseTime - 2.hours), // Before cache - ignored
      createTransaction(confirmationTime = baseTime - 30.minutes), // After cache - included
      createTransaction(confirmationTime = baseTime - 15.minutes), // After cache - included
      createTransaction(confirmationTime = null) // Pending - ignored
    )

    val result = service.findTransactionsConfirmedAfterCache(
      confirmedTxs = transactions,
      cachedAt = cachedAt,
      rangeStart = baseTime - DAY.duration,
      rangeEnd = baseTime
    )

    result.shouldHaveSize(2)
    result.shouldBe(
      listOf(
        baseTime - 30.minutes,
        baseTime - 15.minutes
      )
    )
  }
})

// Helper function to create test transactions
private fun createTransaction(
  id: String = "test-tx",
  confirmationTime: Instant? = null,
  amount: BitcoinMoney = BitcoinMoney.sats(50000),
): BitcoinTransaction {
  val confirmationStatus = confirmationTime?.let {
    Confirmed(BlockTime(800000, it))
  } ?: Pending

  return BitcoinTransaction(
    id = id,
    recipientAddress = null,
    broadcastTime = null,
    estimatedConfirmationTime = null,
    confirmationStatus = confirmationStatus,
    vsize = 250UL,
    weight = 1000UL,
    fee = BitcoinMoney.sats(1000),
    subtotal = amount,
    total = amount + BitcoinMoney.sats(1000),
    transactionType = Incoming,
    inputs = emptyImmutableList(),
    outputs = emptyImmutableList()
  )
}

// Fake implementations for testing
private class FakeCurrencyConverter : CurrencyConverter {
  override fun convert(
    fromAmount: Money,
    toCurrency: Currency,
    rates: List<ExchangeRate>,
  ): Money? = null

  override fun convert(
    fromAmount: Money,
    toCurrency: Currency,
    atTime: Instant?,
  ): Flow<Money?> = flowOf(null)

  override fun latestRateTimestamp(
    fromCurrency: Currency,
    toCurrency: Currency,
  ): Flow<Instant?> = flowOf(null)
}

private class FakeFiatCurrencyPreferenceRepository : FiatCurrencyPreferenceRepository {
  override val fiatCurrencyPreference = MutableStateFlow(
    FiatCurrency(
      textCode = IsoCurrencyTextCode("USD"),
      unitSymbol = "$",
      fractionalDigits = 2,
      displayConfiguration = FiatCurrency.DisplayConfiguration(
        name = "US Dollar",
        displayCountryCode = "US"
      )
    )
  )

  override suspend fun setFiatCurrencyPreference(fiatCurrency: FiatCurrency) = Ok(Unit)

  override suspend fun clear() = Ok(Unit)
}

private class FakeChartDataFetcherService : ChartDataFetcherService {
  override suspend fun getChartData(
    range: ChartRange,
    maxPricePoints: Int?,
  ) = Ok(emptyList<DataPoint>())
}

private class FakeBitcoinWalletService : BitcoinWalletService {
  override fun spendingWallet() = MutableStateFlow(null)

  override suspend fun sync() = Ok(Unit)

  override fun transactionsData() = MutableStateFlow(null)

  override suspend fun broadcast(
    psbt: Psbt,
    estimatedTransactionPriority: EstimatedTransactionPriority,
  ) = throw NotImplementedError()

  override suspend fun createPsbtsForSendAmount(
    sendAmount: BitcoinTransactionSendAmount,
    recipientAddress: BitcoinAddress,
  ) = throw NotImplementedError()
}

private class FakeTransactionsActivityService : TransactionsActivityService {
  override val transactions = MutableStateFlow<List<Transaction>?>(emptyList())
  override val activeAndInactiveWalletTransactions = MutableStateFlow<List<Transaction>?>(emptyList())

  override suspend fun sync() = Ok(Unit)

  override suspend fun syncActiveAndInactiveWallets() = Ok(Unit)

  override fun transactionById(transactionId: String) = flowOf(null)
}
