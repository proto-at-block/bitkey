package build.wallet.pricechart

import app.cash.sqldelight.async.coroutines.awaitAsOneOrNull
import build.wallet.activity.Transaction
import build.wallet.activity.TransactionsActivityService
import build.wallet.bitcoin.transactions.BitcoinTransaction
import build.wallet.bitcoin.transactions.BitcoinTransaction.ConfirmationStatus.Confirmed
import build.wallet.bitcoin.transactions.BitcoinTransaction.TransactionType
import build.wallet.bitcoin.transactions.BitcoinWalletService
import build.wallet.database.BitkeyDatabaseProvider
import build.wallet.database.sqldelight.WalletBalanceEntity
import build.wallet.database.sqldelight.WalletBalanceQueries
import build.wallet.di.AppScope
import build.wallet.di.BitkeyInject
import build.wallet.logging.logError
import build.wallet.money.currency.code.IsoCurrencyTextCode
import build.wallet.money.display.FiatCurrencyPreferenceRepository
import build.wallet.money.exchange.CurrencyConverter
import build.wallet.money.exchange.ExchangeRate
import build.wallet.pricechart.ChartRange.DAY
import build.wallet.pricechart.ChartRange.WEEK
import build.wallet.sqldelight.asFlowOfList
import build.wallet.sqldelight.awaitTransactionWithResult
import build.wallet.time.truncateTo
import com.github.michaelbull.result.Ok
import com.github.michaelbull.result.Result
import com.github.michaelbull.result.getOr
import com.github.michaelbull.result.getOrElse
import com.github.michaelbull.result.map
import com.ionspin.kotlin.bignum.decimal.BigDecimal
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlin.collections.map
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.roundToInt
import kotlin.time.Duration.Companion.seconds

@BitkeyInject(AppScope::class)
class BalanceHistoryServiceImpl(
  private val databaseProvider: BitkeyDatabaseProvider,
  private val currencyConverter: CurrencyConverter,
  private val fiatCurrencyPreferenceRepository: FiatCurrencyPreferenceRepository,
  private val chartDataFetcherService: ChartDataFetcherService,
  private val bitcoinWalletService: BitcoinWalletService,
  private val transactionsActivityService: TransactionsActivityService,
  private val clock: Clock,
  private val appScope: CoroutineScope,
) : BalanceHistoryService {
  override fun clearData() {
    appScope.launch {
      databaseProvider
        .database()
        .walletBalanceQueries
        .clearAll()
    }
  }

  override fun observe(range: ChartRange): Flow<Result<List<BalanceAt>, Error>> {
    return flow {
      updateBalanceHistory(range)

      val rangeStart = clock.now().truncateTo(range.interval) - range.duration

      // Get cached Bitcoin balances and transform to BalanceAt with fresh exchange rates
      val balanceAtFlow = databaseProvider.database()
        .walletBalanceQueries
        .selectFrom(rangeStart, range)
        .asFlowOfList()
        .mapNotNull { result ->
          result.getOr(null)
        }
        .flatMapLatest { bitcoinBalances ->
          flow {
            if (bitcoinBalances.isEmpty()) {
              emit(Ok(emptyList()))
            } else {
              try {
                val fiatCurrency = fiatCurrencyPreferenceRepository.fiatCurrencyPreference.first()

                // Fetch fresh exchange rates for the time range
                val exchangeRates = loadChartData(range, bitcoinBalances.size, fiatCurrency.textCode)
                  .getOrElse {
                    logError(throwable = it) { "Failed to load exchange rates for balance display" }
                    emit(Ok(emptyList()))
                    return@flow
                  }

                // Align exchange rates with Bitcoin balance timestamps
                val alignedRates = alignExchangeRatesToIntervals(
                  intervals = bitcoinBalances.map { it.date },
                  exchangeRates = exchangeRates
                )

                // Calculate fiat balances on-the-fly
                val balanceAtList = bitcoinBalances.mapIndexed { index, bitcoinBalance ->
                  val rate = alignedRates.getOrNull(index)?.rate ?: 0.0
                  val fiatBalance = bitcoinBalance.btcBalance * rate

                  BalanceAt(
                    date = bitcoinBalance.date,
                    balance = bitcoinBalance.btcBalance,
                    fiatBalance = fiatBalance
                  )
                }

                emit(Ok(balanceAtList))
              } catch (e: ArithmeticException) {
                logError(throwable = e) { "Arithmetic error calculating fiat balances" }
                emit(Ok(emptyList()))
              } catch (e: IllegalStateException) {
                logError(throwable = e) { "Invalid state while calculating fiat balances" }
                emit(Ok(emptyList()))
              }
            }
          }
        }

      emitAll(
        combine(balanceAtFlow, liveBalance(range)) { data, liveBalance ->
          data.map { balanceList ->
            balanceList + listOfNotNull(liveBalance)
          }
        }.distinctUntilChanged()
      )
    }.flowOn(Dispatchers.IO)
  }

  /**
   * Produces the current [BalanceAt] while the current time
   * is over 1 minute past the last checkpoint, updating the
   * balance and rate every minute until the next checkpoint.
   *
   * While within 1 minute of the last checkpoint, returns null
   * allowing the database to provide the 'live' balance.
   */
  private fun liveBalance(range: ChartRange): Flow<BalanceAt?> {
    return flow {
      while (true) {
        val startTime = clock.now()
        val aligned = startTime.truncateTo(range.interval)
        val activeWallet = bitcoinWalletService.spendingWallet()
          .filterNotNull()
          .first()

        val isNewWallet = activeWallet.transactions().first().isEmpty()
        if (isNewWallet) {
          emit(null)
        }

        val fiatCurrency = fiatCurrencyPreferenceRepository.fiatCurrencyPreference.value
        var nextCheckpoint = (aligned + range.interval) - startTime
        while (nextCheckpoint.inWholeSeconds > 0) {
          val balance = activeWallet.balance().first()
          val fiatBalance = currencyConverter.convert(
            fromAmount = balance.confirmed,
            toCurrency = fiatCurrency,
            atTime = null
          ).filterNotNull().first()

          val now = clock.now()
          val balanceAt = BalanceAt(
            date = now,
            balance = balance.confirmed.value.doubleValue(false),
            fiatBalance = fiatBalance.value.doubleValue(false)
          )
          emit(balanceAt.takeIf { !fiatBalance.isZero })
          // delay the next refresh until the top of the next minute
          // or the next checkpoint time, whichever is sooner.
          val refreshDelay = minOf(
            nextCheckpoint,
            // the duration until the top of the next minute
            (60 - (now.epochSeconds % 60)).seconds
          )
          nextCheckpoint -= refreshDelay
          delay(refreshDelay)
        }
        updateBalanceHistory(range)
      }
    }
  }

  /**
   * Update the balance history database for the [range]
   * starting from the beginning of the range or the last
   * recorded point, until the end of the range.
   *
   * Note that this operation aligns the final data point to
   * the end of the most recent [ChartRange.interval],
   * the current data point is produced by [liveBalance].
   */
  private suspend fun updateBalanceHistory(range: ChartRange) {
    // Ensure we have comprehensive transaction data including inactive wallets
    transactionsActivityService.syncActiveAndInactiveWallets()

    val queries = databaseProvider.database().walletBalanceQueries

    // Align the current time to the last interval
    val alignedEnd = clock.now().truncateTo(range.interval)
    val alignedStart = alignedEnd - range.duration

    queries.clearUnusedData(range, alignedStart)

    val confirmedTxs = loadConfirmedTransactions()
    if (confirmedTxs.isEmpty()) return

    val latestPoint = queries.selectLatest(range).awaitAsOneOrNull()

    // Clear cache if we have transactions confirmed after cache creation
    clearStaleCache(latestPoint, alignedStart, alignedEnd, confirmedTxs, range, queries)

    // Standard start point calculation (unchanged)
    val start = latestPoint?.date?.plus(range.interval) ?: alignedStart

    val intervalCount = ceil((alignedEnd - start) / range.interval).roundToInt()
    if (alignedEnd != start && intervalCount == 0) return

    val rangeIntervals = List(intervalCount + 1) { index ->
      start + (range.interval * index)
    }

    val walletBalancePoints = generateBalanceEntities(
      confirmedTxs = confirmedTxs,
      start = start,
      rangeIntervals = rangeIntervals,
      range = range
    )

    // Write in background to avoid cancellation and connect observer flows immediately
    appScope.launch {
      queries.awaitTransactionWithResult {
        walletBalancePoints.forEach(::insertBalance)
      }
    }
  }

  /**
   * Clears stale cache entries if there are transactions confirmed after cache creation.
   */
  private suspend fun clearStaleCache(
    latestPoint: WalletBalanceEntity?,
    alignedStart: Instant,
    alignedEnd: Instant,
    confirmedTxs: List<BitcoinTransaction>,
    range: ChartRange,
    queries: WalletBalanceQueries,
  ) {
    if (latestPoint == null) return

    // Find transactions that were confirmed AFTER our cache was created
    val transactionsConfirmedAfterCache = findTransactionsConfirmedAfterCache(
      confirmedTxs = confirmedTxs,
      cachedAt = latestPoint.cachedAt,
      rangeStart = alignedStart,
      rangeEnd = alignedEnd
    )

    if (transactionsConfirmedAfterCache.isNotEmpty()) {
      // Clear cache from the earliest affected transaction point
      val earliestAffectedTime = transactionsConfirmedAfterCache.min()
      val clearFromTime = maxOf(alignedStart, earliestAffectedTime.truncateTo(range.interval))
      queries.clearFromDate(clearFromTime, range)
    }
  }

  /**
   * Finds transactions that were confirmed after the cache was created.
   */
  internal fun findTransactionsConfirmedAfterCache(
    confirmedTxs: List<BitcoinTransaction>,
    cachedAt: Instant,
    rangeStart: Instant,
    rangeEnd: Instant,
  ): List<Instant> {
    return confirmedTxs.mapNotNull { tx ->
      tx.confirmationTime()?.takeIf { confirmationTime ->
        // Transaction was confirmed after our cache was created
        // AND the transaction affects this range
        confirmationTime > cachedAt &&
          confirmationTime in rangeStart..rangeEnd
      }
    }
  }

  private fun generateBalanceEntities(
    confirmedTxs: List<BitcoinTransaction>,
    start: Instant,
    rangeIntervals: List<Instant>,
    range: ChartRange,
  ): List<WalletBalanceEntity> {
    val txsBeforeStart = confirmedTxs.takeWhile {
      it.confirmationTime()?.let { confirmationTime -> confirmationTime < start } == true
    }
    val txsAfterStart = confirmedTxs.drop(txsBeforeStart.size).toMutableList()

    var balance = BigDecimal.ZERO.applyTransactions(txsBeforeStart)

    // Trim any intervals before the first transaction from the head of the list
    val intervals = when {
      txsBeforeStart.isEmpty() && txsAfterStart.isNotEmpty() -> {
        val firstTxTime = txsAfterStart.first().confirmationTime()
        if (firstTxTime != null) {
          rangeIntervals.dropWhile { it < firstTxTime }
        } else {
          rangeIntervals
        }
      }
      else -> rangeIntervals
    }

    val cachedAt = clock.now()
    val walletBalancePoints = intervals.map { timestamp ->
      // update the balance with transactions confirmed before this checkpoint
      val pastTxs = txsAfterStart.takeWhile {
        it.confirmationTime()?.let { confirmationTime -> confirmationTime < timestamp } == true
      }
      txsAfterStart.removeAll(pastTxs)
      balance = balance.applyTransactions(pastTxs)

      WalletBalanceEntity(
        date = timestamp,
        btcBalance = balance.doubleValue(exactRequired = false),
        range = range,
        cachedAt = cachedAt
      )
    }
    return walletBalancePoints
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

  private fun WalletBalanceQueries.clearUnusedData(
    range: ChartRange,
    alignedStart: Instant,
  ) {
    // Clear DAY/WEEK data points since they are never used after ChartRange.duration
    when (range) {
      DAY, WEEK -> clearStale(alignedStart, range)
      else -> Unit
    }
  }
}
