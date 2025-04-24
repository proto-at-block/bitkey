package build.wallet.pricechart

import app.cash.sqldelight.async.coroutines.awaitAsOneOrNull
import build.wallet.account.AccountService
import build.wallet.account.getAccount
import build.wallet.bitcoin.BitcoinNetworkType
import build.wallet.bitcoin.descriptor.BitcoinMultiSigDescriptorBuilder
import build.wallet.bitcoin.transactions.BitcoinTransaction
import build.wallet.bitcoin.transactions.BitcoinTransaction.ConfirmationStatus.Confirmed
import build.wallet.bitcoin.transactions.BitcoinTransaction.TransactionType
import build.wallet.bitcoin.transactions.BitcoinWalletService
import build.wallet.bitcoin.wallet.WatchingWallet
import build.wallet.bitcoin.wallet.WatchingWalletDescriptor
import build.wallet.bitcoin.wallet.WatchingWalletProvider
import build.wallet.bitkey.account.FullAccount
import build.wallet.bitkey.spending.SpendingKeyset
import build.wallet.database.BitkeyDatabaseProvider
import build.wallet.database.sqldelight.WalletBalanceEntity
import build.wallet.database.sqldelight.WalletBalanceQueries
import build.wallet.di.AppScope
import build.wallet.di.BitkeyInject
import build.wallet.f8e.recovery.ListKeysetsF8eClient
import build.wallet.logging.logError
import build.wallet.logging.logFailure
import build.wallet.money.BitcoinMoney
import build.wallet.money.currency.FiatCurrency
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
import com.github.michaelbull.result.coroutines.coroutineBinding
import com.github.michaelbull.result.getOr
import com.github.michaelbull.result.getOrElse
import com.github.michaelbull.result.map
import com.github.michaelbull.result.onSuccess
import com.ionspin.kotlin.bignum.decimal.BigDecimal
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart.LAZY
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlin.collections.map
import kotlin.math.ceil
import kotlin.math.roundToInt
import kotlin.time.Duration.Companion.seconds

@BitkeyInject(AppScope::class)
class BalanceHistoryServiceImpl(
  private val databaseProvider: BitkeyDatabaseProvider,
  private val currencyConverter: CurrencyConverter,
  private val accountService: AccountService,
  private val watchingWalletProvider: WatchingWalletProvider,
  private val bitcoinMultiSigDescriptorBuilder: BitcoinMultiSigDescriptorBuilder,
  private val listKeysetsF8eClient: ListKeysetsF8eClient,
  private val fiatCurrencyPreferenceRepository: FiatCurrencyPreferenceRepository,
  private val chartDataFetcherService: ChartDataFetcherService,
  private val bitcoinWalletService: BitcoinWalletService,
  private val clock: Clock,
  private val appScope: CoroutineScope,
) : BalanceHistoryService {
  private val inactiveWallets =
    appScope.async(start = LAZY) { fetchAndSyncInactiveWallets() }

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
      val query = databaseProvider.database()
        .walletBalanceQueries
        .selectFrom(rangeStart, range)
        .asFlowOfList()
        .mapNotNull { result ->
          result
            .map { entities ->
              entities.map { entity ->
                BalanceAt(
                  date = entity.date,
                  balance = entity.btcBalance,
                  fiatBalance = entity.fiatBalance
                )
              }
            }
            .getOr(null)
        }
      emitAll(
        combine(query, liveBalance(range)) { data, liveBalance ->
          Ok(data + listOfNotNull(liveBalance))
        }.distinctUntilChanged()
      )
    }
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
    val fiatCurrency = fiatCurrencyPreferenceRepository.fiatCurrencyPreference.first()
    val queries = databaseProvider.database().walletBalanceQueries

    // Align the current time to the last interval
    val alignedEnd = clock.now().truncateTo(range.interval)
    val alignedStart = alignedEnd - range.duration

    queries.clearUnusedData(range, alignedStart, fiatCurrency.textCode)

    // Start the range from the latest stored point
    val latestPoint = queries.selectLatest(range).awaitAsOneOrNull()
    val start = when {
      latestPoint == null -> alignedStart
      else -> latestPoint.date + range.interval
    }

    // Create a list of Instants for the remaining range intervals
    val intervalCount = ceil((alignedEnd - start) / range.interval).roundToInt()
    if (alignedEnd != start && intervalCount == 0) {
      return // nothing to do
    }
    val rangeIntervals = List(intervalCount + 1) { index ->
      start + (range.interval * index)
    }

    // Fetch the price chart data which will have exchange rates aligned to our data points
    val exchangeRates = loadChartData(range, rangeIntervals.size, fiatCurrency.textCode)
      .getOrElse {
        logError(throwable = it) { "Failed to load rate data" }
        return@updateBalanceHistory
      }

    val syncAll = queries.count(range).executeAsOne() == 0L
    val confirmedTxs = loadConfirmedTransactions(syncAll)
    if (confirmedTxs.isEmpty()) {
      return
    }

    val walletBalancePoints = generateBalanceEntities(
      confirmedTxs = confirmedTxs,
      start = start,
      rangeIntervals = rangeIntervals,
      exchangeRates = exchangeRates,
      fiatCurrency = fiatCurrency,
      range = range
    )

    // Write in background to avoid cancellation and connect observer flows immediately
    appScope.launch {
      queries.awaitTransactionWithResult {
        walletBalancePoints.forEach(::insertBalance)
      }
    }
  }

  private fun generateBalanceEntities(
    confirmedTxs: List<BitcoinTransaction>,
    start: Instant,
    rangeIntervals: List<Instant>,
    exchangeRates: List<ExchangeRate>,
    fiatCurrency: FiatCurrency,
    range: ChartRange,
  ): List<WalletBalanceEntity> {
    val txsBeforeStart = confirmedTxs.takeWhile { it.confirmationTime()!! < start }
    val txsAfterStart = confirmedTxs.drop(txsBeforeStart.size).toMutableList()

    var balance = BigDecimal.ZERO.applyTransactions(txsBeforeStart)

    val intervals = when {
      txsBeforeStart.isEmpty() -> {
        val firstTxTime = txsAfterStart.first().confirmationTime()!!
        rangeIntervals.dropWhile { it < firstTxTime }
      }
      else -> rangeIntervals
    }
    val newExchangeRates = exchangeRates.drop(rangeIntervals.size - intervals.size)

    val walletBalancePoints = intervals.mapIndexed { index, timestamp ->
      // update the balance with transactions confirmed before this checkpoint
      val pastTxs = txsAfterStart.takeWhile { it.confirmationTime()!! < timestamp }
      txsAfterStart.removeAll(pastTxs)
      balance = balance.applyTransactions(pastTxs)

      val bitcoinBalance = BitcoinMoney.btc(balance)
      val rates = listOf(newExchangeRates.getOrElse(index) { exchangeRates.last() })
      val fiatBalance =
        checkNotNull(currencyConverter.convert(bitcoinBalance, fiatCurrency, rates))
      WalletBalanceEntity(
        date = timestamp,
        fiatCurrencyCode = fiatCurrency.textCode,
        btcBalance = balance.doubleValue(exactRequired = false),
        fiatBalance = fiatBalance.value.doubleValue(exactRequired = false),
        range = range
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
   * Using the current account in [AccountService], fetch the inactive keysets
   * as [WatchingWallet] instances and sync them.
   */
  private suspend fun fetchAndSyncInactiveWallets(): Result<List<WatchingWallet>, Throwable> =
    coroutineBinding {
      val account = accountService.getAccount<FullAccount>().bind()
      val environment = account.config.f8eEnvironment
      val activeKeysetId = account.keybox.activeSpendingKeyset.f8eSpendingKeyset.keysetId

      listKeysetsF8eClient
        .listKeysets(environment, account.accountId)
        .logFailure { "Error fetching keysets for balance history tracking." }
        .bind()
        .filter { it.f8eSpendingKeyset.keysetId != activeKeysetId }
        .map {
          val descriptor = it.toWalletDescriptor(account.config.bitcoinNetworkType)
          async {
            watchingWalletProvider.getWallet(descriptor)
              .onSuccess { wallet ->
                wallet.sync()
              }
              .bind()
          }
        }.awaitAll()
    }.logFailure { "Failed to load inactive wallets" }

  private suspend fun loadConfirmedTransactions(syncAll: Boolean): List<BitcoinTransaction> {
    // load inactive wallets if we have not checked them before
    val inactiveWallets = if (syncAll) {
      inactiveWallets.await()
        .getOrElse {
          logError(throwable = it) { "Failed to load inactive wallets" }
          emptyList()
        }
    } else {
      emptyList()
    }

    val activeWallet = bitcoinWalletService.spendingWallet()
      .filterNotNull()
      .first()

    return (inactiveWallets + activeWallet)
      .flatMap { wallet ->
        wallet.transactions()
          .first()
          .filter { it.confirmationStatus is Confirmed }
      }
      .sortedBy { it.confirmationTime() }
  }

  private fun WalletBalanceQueries.clearUnusedData(
    range: ChartRange,
    alignedStart: Instant,
    fiatCurrencyCode: IsoCurrencyTextCode,
  ) {
    // Clear DAY/WEEK data points since they are never used after ChartRange.duration
    when (range) {
      DAY, WEEK -> clearStale(alignedStart, range)
      else -> Unit
    }
    // Clear any values not using the selected currency, forcing all data to recompute
    clearIfNotCurrencyCode(fiatCurrencyCode)
  }

  private fun SpendingKeyset.toWalletDescriptor(
    networkType: BitcoinNetworkType,
  ): WatchingWalletDescriptor {
    return WatchingWalletDescriptor(
      identifier = "WatchingWallet $localId",
      networkType = networkType,
      receivingDescriptor = bitcoinMultiSigDescriptorBuilder
        .watchingReceivingDescriptor(
          appPublicKey = appKey.key,
          hardwareKey = hardwareKey.key,
          serverKey = f8eSpendingKeyset.spendingPublicKey.key
        ),
      changeDescriptor = bitcoinMultiSigDescriptorBuilder
        .watchingChangeDescriptor(
          appPublicKey = appKey.key,
          hardwareKey = hardwareKey.key,
          serverKey = f8eSpendingKeyset.spendingPublicKey.key
        )
    )
  }
}
