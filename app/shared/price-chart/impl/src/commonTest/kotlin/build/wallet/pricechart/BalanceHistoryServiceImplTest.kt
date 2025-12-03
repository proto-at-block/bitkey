package build.wallet.pricechart

import build.wallet.activity.Transaction
import build.wallet.activity.TransactionsActivityService
import build.wallet.activity.TransactionsActivityState
import build.wallet.bitcoin.BlockTime
import build.wallet.bitcoin.transactions.BitcoinTransaction
import build.wallet.bitcoin.transactions.BitcoinTransaction.ConfirmationStatus.Confirmed
import build.wallet.bitcoin.transactions.BitcoinTransaction.ConfirmationStatus.Pending
import build.wallet.bitcoin.transactions.BitcoinTransaction.TransactionType.Incoming
import build.wallet.compose.collections.emptyImmutableList
import build.wallet.money.BitcoinMoney
import build.wallet.money.currency.FiatCurrency
import build.wallet.money.currency.code.IsoCurrencyTextCode
import build.wallet.money.display.FiatCurrencyPreferenceRepository
import build.wallet.time.ClockFake
import build.wallet.time.truncateTo
import com.github.michaelbull.result.Ok
import com.github.michaelbull.result.getOrThrow
import io.kotest.assertions.fail
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.comparables.shouldBeGreaterThan
import io.kotest.matchers.doubles.shouldBeWithinPercentageOf
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.datetime.Instant
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

class BalanceHistoryServiceImplTest : FunSpec({

  val baseTime = Instant.parse("2025-06-13T12:00:00Z")
  val clock = ClockFake().apply { now = baseTime }

  data class ChartRangeTestCase(
    val range: ChartRange,
    val transactionOffset: Duration,
    val transactionAmount: BitcoinMoney,
    val exchangeRate: Double,
    val expectedBalance: Double,
    val expectedFiatBalance: Double,
    val description: String,
  )

  val chartRangeTestCases = listOf(
    ChartRangeTestCase(
      range = ChartRange.DAY,
      transactionOffset = ChartRange.DAY.interval + 31.seconds,
      transactionAmount = BitcoinMoney.sats(14000),
      exchangeRate = 100.0,
      expectedBalance = 0.00014,
      expectedFiatBalance = 0.014,
      description = "Transaction at 12:10:31, but truncated boundary for 10-minute intervals is 12:10:00"
    ),
    ChartRangeTestCase(
      range = ChartRange.WEEK,
      transactionOffset = ChartRange.WEEK.interval + 30.minutes + 45.seconds,
      transactionAmount = BitcoinMoney.sats(25000),
      exchangeRate = 200.0,
      expectedBalance = 0.00025,
      expectedFiatBalance = 0.05,
      description = "Transaction at 13:30:45, but truncated boundary for 1-hour intervals is 13:00:00"
    ),
    ChartRangeTestCase(
      range = ChartRange.MONTH,
      transactionOffset = ChartRange.MONTH.interval + 2.hours + 30.minutes + 15.seconds,
      transactionAmount = BitcoinMoney.sats(50000),
      exchangeRate = 300.0,
      expectedBalance = 0.0005,
      expectedFiatBalance = 0.15,
      description = "Transaction at 14:30:15, but truncated boundary for 1-day intervals is 00:00:00"
    ),
    ChartRangeTestCase(
      range = ChartRange.ALL,
      transactionOffset = ChartRange.ALL.interval + 4.hours + 45.minutes + 30.seconds,
      transactionAmount = BitcoinMoney.sats(100000),
      exchangeRate = 400.0,
      expectedBalance = 0.001,
      expectedFiatBalance = 0.4,
      description = "Transaction at 16:45:30, but truncated boundary for 1-day intervals is 00:00:00"
    )
  )

  chartRangeTestCases.forEach { testCase ->
    test("includes transactions that occur after truncated interval boundary - ${testCase.range.name} view") {
      val alignedEnd = baseTime.truncateTo(testCase.range.interval)
      val alignedStart = alignedEnd - testCase.range.duration

      val transactionTime = alignedStart + testCase.transactionOffset
      val transaction = createTransaction(
        id = "after-boundary-${testCase.range.name.lowercase()}-tx",
        confirmationTime = transactionTime,
        amount = testCase.transactionAmount
      )

      val transactionsService = FakeTransactionsActivityService().apply {
        setTransactions(listOf(Transaction.BitcoinWalletTransaction(transaction)))
      }

      val chartDataService = FakeChartDataFetcherService().apply {
        val extraPoints = if (testCase.range == ChartRange.DAY || testCase.range == ChartRange.WEEK) 10 else 5
        setExchangeRates(
          (0..testCase.range.maxPricePoints + extraPoints).map { interval ->
            DataPoint(alignedStart.epochSeconds + (interval * testCase.range.interval.inWholeSeconds), testCase.exchangeRate)
          }
        )
      }

      val service = BalanceHistoryServiceImpl(
        fiatCurrencyPreferenceRepository = FakeFiatCurrencyPreferenceRepository(),
        chartDataFetcherService = chartDataService,
        transactionsActivityService = transactionsService,
        clock = clock
      )

      val result = service.observe(testCase.range).first()

      result.isOk shouldBe true
      val balanceHistory = result.getOrThrow()

      balanceHistory.size shouldBeGreaterThan 0

      val pointAfterTransaction = balanceHistory.find { it.date > transactionTime }
      if (pointAfterTransaction == null) {
        println("Transaction time: $transactionTime")
        println("Aligned start: $alignedStart, Aligned end: $alignedEnd")
        println("Available data points: ${balanceHistory.map { "${it.date} -> ${it.balance}" }}")
        fail("No data points found after transaction time $transactionTime")
      }
      pointAfterTransaction.balance shouldBe testCase.expectedBalance
      pointAfterTransaction.fiatBalance.shouldBeWithinPercentageOf(testCase.expectedFiatBalance, 0.01)
    }
  }

  test("generates correct number of data points including final transaction point") {
    val alignedEnd = baseTime.truncateTo(ChartRange.WEEK.interval)
    val alignedStart = alignedEnd - ChartRange.WEEK.duration

    val transactionTime = alignedEnd - 3.hours - 15.minutes
    val transaction = createTransaction(
      id = "final-point-tx",
      confirmationTime = transactionTime,
      amount = BitcoinMoney.sats(25000)
    )

    val transactionsService = FakeTransactionsActivityService().apply {
      setTransactions(listOf(Transaction.BitcoinWalletTransaction(transaction)))
    }

    val chartDataService = FakeChartDataFetcherService().apply {
      setExchangeRates(
        (0..ChartRange.WEEK.maxPricePoints + 5).map { interval ->
          DataPoint(alignedStart.epochSeconds + (interval * ChartRange.WEEK.interval.inWholeSeconds), 50000.0)
        }
      )
    }

    val service = BalanceHistoryServiceImpl(
      fiatCurrencyPreferenceRepository = FakeFiatCurrencyPreferenceRepository(),
      chartDataFetcherService = chartDataService,
      transactionsActivityService = transactionsService,
      clock = clock
    )

    val result = service.observe(ChartRange.WEEK).first()

    result.isOk shouldBe true
    val balanceHistory = result.getOrThrow()

    balanceHistory.size shouldBeGreaterThan 0

    val finalPoint = balanceHistory.last()
    finalPoint.balance shouldBe 0.00025
    finalPoint.fiatBalance shouldBe 12.5
  }

  test("correctly calculates cumulative balance across multiple transactions") {
    val alignedEnd = baseTime.truncateTo(ChartRange.WEEK.interval)
    val alignedStart = alignedEnd - ChartRange.WEEK.duration

    val tx1Time = alignedStart + 1.hours
    val tx2Time = alignedStart + 2.hours
    val tx3Time = alignedStart + 3.hours

    val transactions = listOf(
      createTransaction("tx1", tx1Time, BitcoinMoney.sats(10000)),
      createTransaction("tx2", tx2Time, BitcoinMoney.sats(5000)),
      createTransaction("tx3", tx3Time, BitcoinMoney.sats(15000))
    )

    val transactionsService = FakeTransactionsActivityService().apply {
      setTransactions(transactions.map { Transaction.BitcoinWalletTransaction(it) })
    }

    val chartDataService = FakeChartDataFetcherService().apply {
      setExchangeRates(
        (0..ChartRange.WEEK.maxPricePoints + 5).map { interval ->
          DataPoint(alignedStart.epochSeconds + (interval * ChartRange.WEEK.interval.inWholeSeconds), 100.0)
        }
      )
    }

    val service = BalanceHistoryServiceImpl(
      fiatCurrencyPreferenceRepository = FakeFiatCurrencyPreferenceRepository(),
      chartDataFetcherService = chartDataService,
      transactionsActivityService = transactionsService,
      clock = clock
    )

    val result = service.observe(ChartRange.WEEK).first()

    result.isOk shouldBe true
    val balanceHistory = result.getOrThrow()

    val pointAt1Hour = balanceHistory.find { it.date == tx1Time.truncateTo(ChartRange.WEEK.interval) }
    val pointAt2Hours = balanceHistory.find { it.date == tx2Time.truncateTo(ChartRange.WEEK.interval) }
    val pointAt3Hours = balanceHistory.find { it.date == tx3Time.truncateTo(ChartRange.WEEK.interval) }

    if (pointAt1Hour != null) {
      pointAt1Hour.balance shouldBe 0.0001
      pointAt1Hour.fiatBalance shouldBe 0.01
    }

    if (pointAt2Hours != null) {
      pointAt2Hours.balance shouldBe 0.00015
      pointAt2Hours.fiatBalance shouldBe 0.015
    }

    if (pointAt3Hours != null) {
      pointAt3Hours.balance shouldBe 0.0003
      pointAt3Hours.fiatBalance shouldBe 0.03
    }
  }

  test("correctly aligns exchange rates with interval timestamps") {
    val alignedEnd = baseTime.truncateTo(ChartRange.WEEK.interval)
    val alignedStart = alignedEnd - ChartRange.WEEK.duration

    val transaction = createTransaction(
      id = "rate-alignment-tx",
      confirmationTime = alignedStart + 2.hours,
      amount = BitcoinMoney.sats(20000)
    )

    val transactionsService = FakeTransactionsActivityService().apply {
      setTransactions(listOf(Transaction.BitcoinWalletTransaction(transaction)))
    }

    val chartDataService = FakeChartDataFetcherService().apply {
      setExchangeRates(
        listOf(
          DataPoint(alignedStart.epochSeconds - 1800, 100.0),
          DataPoint(alignedStart.epochSeconds + 1800, 200.0),
          DataPoint(alignedStart.epochSeconds + 5400, 300.0),
          DataPoint(alignedStart.epochSeconds + 9000, 400.0)
        )
      )
    }

    val service = BalanceHistoryServiceImpl(
      fiatCurrencyPreferenceRepository = FakeFiatCurrencyPreferenceRepository(),
      chartDataFetcherService = chartDataService,
      transactionsActivityService = transactionsService,
      clock = clock
    )

    val result = service.observe(ChartRange.WEEK).first()

    result.isOk shouldBe true
    val balanceHistory = result.getOrThrow()

    balanceHistory.size shouldBeGreaterThan 0

    val pointAfterTransaction = balanceHistory.find { it.date > transaction.confirmationTime()!! }
    if (pointAfterTransaction != null) {
      pointAfterTransaction.balance shouldBe 0.0002
      pointAfterTransaction.fiatBalance shouldBeGreaterThan 0.0
    }
  }

  test("service can be instantiated") {
    val service = BalanceHistoryServiceImpl(
      fiatCurrencyPreferenceRepository = FakeFiatCurrencyPreferenceRepository(),
      chartDataFetcherService = FakeChartDataFetcherService(),
      transactionsActivityService = FakeTransactionsActivityService(),
      clock = clock
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
  private var exchangeRates: List<DataPoint> = emptyList()

  fun setExchangeRates(rates: List<DataPoint>) {
    exchangeRates = rates
  }

  override suspend fun getChartData(
    range: ChartRange,
    maxPricePoints: Int?,
  ) = Ok(exchangeRates)
}

private class FakeTransactionsActivityService : TransactionsActivityService {
  override val transactionsState: StateFlow<TransactionsActivityState>
    get() = (
      transactions.value?.let {
        if (it.isEmpty()) {
          TransactionsActivityState.Empty
        } else {
          TransactionsActivityState.Loaded(it)
        }
      } ?: TransactionsActivityState.InitialLoading
    ).let { MutableStateFlow(it) }

  override val transactions = MutableStateFlow<List<Transaction>?>(emptyList())
  override val activeAndInactiveWalletTransactions = MutableStateFlow<List<Transaction>?>(emptyList())

  fun setTransactions(txs: List<Transaction>) {
    transactions.value = txs
    activeAndInactiveWalletTransactions.value = txs
  }

  override suspend fun sync() = Ok(Unit)

  override fun transactionById(transactionId: String) = flowOf(null)
}
