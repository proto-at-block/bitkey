package build.wallet.pricechart

import build.wallet.activity.Transaction
import build.wallet.activity.TransactionsActivityService
import build.wallet.bitcoin.transactions.BitcoinTransaction
import build.wallet.bitcoin.transactions.BitcoinTransaction.ConfirmationStatus.Confirmed
import build.wallet.bitcoin.transactions.BitcoinTransaction.TransactionType
import build.wallet.di.AppScope
import build.wallet.di.BitkeyInject
import build.wallet.logging.logError
import build.wallet.money.currency.code.IsoCurrencyTextCode
import build.wallet.money.display.FiatCurrencyPreferenceRepository
import build.wallet.money.exchange.ExchangeRate
import build.wallet.time.truncateTo
import com.github.michaelbull.result.Ok
import com.github.michaelbull.result.Result
import com.github.michaelbull.result.getOrElse
import com.github.michaelbull.result.map
import com.ionspin.kotlin.bignum.decimal.BigDecimal
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.roundToInt
import kotlin.time.Duration

@BitkeyInject(AppScope::class)
class BalanceHistoryServiceImpl(
  private val fiatCurrencyPreferenceRepository: FiatCurrencyPreferenceRepository,
  private val chartDataFetcherService: ChartDataFetcherService,
  private val transactionsActivityService: TransactionsActivityService,
  private val clock: Clock,
) : BalanceHistoryService {

  override fun observe(range: ChartRange): Flow<Result<List<BalanceAt>, Error>> {
    return flow {
      // Ensure we have comprehensive transaction data including inactive wallets
      transactionsActivityService.syncActiveAndInactiveWallets()

      // Get confirmed transactions from active and inactive keysets
      val confirmedTxs = loadConfirmedTransactions()

      // Check if this is a new wallet (no confirmed transactions)
      if (confirmedTxs.isEmpty()) {
        emit(Ok(emptyList()))
        return@flow
      }

      val actualEnd = clock.now()
      val alignedEnd = actualEnd.truncateTo(range.interval)
      val alignedStart = alignedEnd - range.duration

      // Extend the range to include transactions up to the current time
      val extendedEnd = if (actualEnd > alignedEnd) {
        alignedEnd + range.interval
      } else {
        alignedEnd
      }

      val rangeIntervals = generateRangeIntervals(alignedStart, extendedEnd, range.interval)

      try {
        val fiatCurrency = fiatCurrencyPreferenceRepository.fiatCurrencyPreference.first()

        // Fetch fresh exchange rates for the time range
        val exchangeRates = loadChartData(range, rangeIntervals.size, fiatCurrency.textCode)
          .getOrElse {
            logError(throwable = it) { "Failed to load exchange rates for balance display" }
            emit(Ok(emptyList()))
            return@flow
          }

        // Generate balance history points using confirmed transactions
        val balanceHistory = generateBalanceHistory(
          confirmedTxs = confirmedTxs,
          rangeIntervals = rangeIntervals,
          exchangeRates = exchangeRates
        )

        emit(Ok(balanceHistory))
      } catch (e: ArithmeticException) {
        logError(throwable = e) { "Arithmetic error generating balance history" }
        emit(Ok(emptyList()))
      } catch (e: IllegalStateException) {
        logError(throwable = e) { "Invalid state generating balance history" }
        emit(Ok(emptyList()))
      }
    }.flowOn(Dispatchers.IO)
  }

  private fun generateRangeIntervals(
    alignedStart: Instant,
    alignedEnd: Instant,
    interval: Duration,
  ): List<Instant> {
    val intervalCount = ceil((alignedEnd - alignedStart) / interval).roundToInt()
    return List(intervalCount + 1) { index ->
      alignedStart + (interval * index)
    }
  }

  private fun generateBalanceHistory(
    confirmedTxs: List<BitcoinTransaction>,
    rangeIntervals: List<Instant>,
    exchangeRates: List<ExchangeRate>,
  ): List<BalanceAt> {
    // Align exchange rates with interval timestamps
    val alignedRates = alignExchangeRatesToIntervals(
      intervals = rangeIntervals,
      exchangeRates = exchangeRates
    )

    var balance = BigDecimal.ZERO
    val txsToProcess = confirmedTxs.toMutableList()

    return rangeIntervals.mapIndexed { index, timestamp ->
      // Apply transactions that occurred before or at this timestamp
      val pastTxs = txsToProcess.takeWhile {
        it.confirmationTime()?.let { confirmationTime -> confirmationTime <= timestamp } == true
      }
      txsToProcess.removeAll(pastTxs)
      balance = balance.applyTransactions(pastTxs)

      val rate = alignedRates.getOrNull(index)?.rate ?: 0.0
      val fiatBalance = balance.doubleValue(exactRequired = false) * rate

      BalanceAt(
        date = timestamp,
        balance = balance.doubleValue(exactRequired = false),
        fiatBalance = fiatBalance
      )
    }
  }

  private suspend fun loadChartData(
    range: ChartRange,
    count: Int,
    fiatCurrencyCode: IsoCurrencyTextCode,
  ) = chartDataFetcherService.getChartData(range, count)
    .map { chartData ->
      chartData
        .takeLast(count)
        .map {
          ExchangeRate(
            fromCurrency = IsoCurrencyTextCode("BTC"),
            toCurrency = fiatCurrencyCode,
            rate = it.y,
            timeRetrieved = Instant.fromEpochSeconds(it.x)
          )
        }
    }

  /**
   * Calculate the new [BigDecimal] starting from the receiver value
   * by applying each [BitcoinTransaction] provided to it.
   */
  private fun BigDecimal.applyTransactions(txs: List<BitcoinTransaction>): BigDecimal {
    return txs.fold(this) { balance, tx ->
      when (tx.transactionType) {
        TransactionType.Incoming -> balance + tx.subtotal.value
        TransactionType.Outgoing -> balance - tx.total.value
        TransactionType.UtxoConsolidation -> balance - (tx.fee?.value ?: BigDecimal.ZERO)
      }
    }
  }

  /**
   * Aligns exchange rates with interval timestamps to ensure each interval has the correct rate.
   * Assumes both intervals and exchangeRates are sorted by time.
   *
   * @param intervals List of timestamps representing the intervals we need rates for
   * @param exchangeRates List of exchange rates available, sorted by time
   * @return List of exchange rates aligned with the intervals, empty list if alignment cannot be achieved
   */
  private fun alignExchangeRatesToIntervals(
    intervals: List<Instant>,
    exchangeRates: List<ExchangeRate>,
  ): List<ExchangeRate> {
    if (intervals.isEmpty() || exchangeRates.isEmpty()) {
      logError { "No intervals or exchange rates provided for your balance graph" }
      return emptyList()
    }

    val alignedRates = ArrayList<ExchangeRate>(intervals.size)
    var rateIndex = 0

    for (interval in intervals) {
      // Move forward through rates until we've passed the interval time
      // or reached the end of rates
      while (rateIndex < exchangeRates.size - 1 &&
        exchangeRates[rateIndex + 1].timeRetrieved.epochSeconds <= interval.epochSeconds
      ) {
        rateIndex++
      }

      // At this point, rateIndex points to the last rate before or at the interval
      // or we're at the last rate
      val currentRate = exchangeRates[rateIndex]

      // If there's a next rate, check which is closer
      val nextRate = if (rateIndex < exchangeRates.size - 1) {
        exchangeRates[rateIndex + 1]
      } else {
        null
      }

      // Choose the closest rate
      val selectedRate = when {
        nextRate == null -> currentRate
        else -> {
          val currentDiff = abs(currentRate.timeRetrieved.epochSeconds - interval.epochSeconds)
          val nextDiff = abs(nextRate.timeRetrieved.epochSeconds - interval.epochSeconds)
          if (currentDiff <= nextDiff) currentRate else nextRate
        }
      }

      alignedRates.add(selectedRate)
    }

    return alignedRates
  }

  private suspend fun loadConfirmedTransactions(): List<BitcoinTransaction> =
    transactionsActivityService.activeAndInactiveWalletTransactions.first()
      ?.mapNotNull { (it as? Transaction.BitcoinWalletTransaction)?.details }
      ?.filter { it.confirmationStatus is Confirmed }
      ?.sortedBy { it.confirmationTime() }
      ?: emptyList()
}
