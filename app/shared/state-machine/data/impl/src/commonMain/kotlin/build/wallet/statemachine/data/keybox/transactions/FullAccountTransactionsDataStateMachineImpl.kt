package build.wallet.statemachine.data.keybox.transactions

import androidx.compose.runtime.*
import build.wallet.LoadableValue
import build.wallet.LoadableValue.InitialLoading
import build.wallet.LoadableValue.LoadedValue
import build.wallet.analytics.events.AppSessionManager
import build.wallet.analytics.events.AppSessionState
import build.wallet.asLoadableValue
import build.wallet.bitcoin.balance.BitcoinBalance
import build.wallet.compose.coroutines.rememberStableCoroutineScope
import build.wallet.money.FiatMoney
import build.wallet.money.currency.FiatCurrency
import build.wallet.money.display.FiatCurrencyPreferenceRepository
import build.wallet.money.exchange.CurrencyConverter
import build.wallet.money.exchange.ExchangeRate
import build.wallet.money.exchange.ExchangeRateSyncer
import build.wallet.statemachine.data.keybox.transactions.FullAccountTransactionsData.FullAccountTransactionsLoadedData
import build.wallet.statemachine.data.keybox.transactions.FullAccountTransactionsData.LoadingFullAccountTransactionsData
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.flow.SharingStarted.Companion.Eagerly
import kotlin.time.Duration.Companion.seconds

class FullAccountTransactionsDataStateMachineImpl(
  private val currencyConverter: CurrencyConverter,
  private val fiatCurrencyPreferenceRepository: FiatCurrencyPreferenceRepository,
  private val appSessionManager: AppSessionManager,
  private val exchangeRateSyncer: ExchangeRateSyncer,
) : FullAccountTransactionsDataStateMachine {
  @Composable
  override fun model(props: FullAccountTransactionsDataProps): FullAccountTransactionsData {
    val sessionState by appSessionManager.appSessionState.collectAsState()

    LaunchedEffect("sync-wallet", props.wallet, sessionState) {
      // Make sure to initialize the balance and transactions before the sync occurs so that
      // the call to sync doesn't block fetching the balance and transactions
      props.wallet.initializeBalanceAndTransactions()
      props.wallet.launchPeriodicSync(
        scope = this,
        interval = 10.seconds
      )
    }

    var isRefreshing by remember { mutableStateOf(false) }
    var fiatBalance: FiatMoney? by remember { mutableStateOf(null) }
    val fiatCurrency by fiatCurrencyPreferenceRepository.fiatCurrencyPreference.collectAsState()

    val balance =
      remember(props.wallet) {
        props.wallet.balance().asLoadableValue()
      }.collectAsState(InitialLoading).value

    val transactions =
      remember(props.wallet) {
        props.wallet.transactions().asLoadableValue()
      }.collectAsState(InitialLoading).value

    val unspentOutputs = remember(props.wallet) {
      props.wallet.unspentOutputs().asLoadableValue()
    }.collectAsState(InitialLoading).value

    val scope = rememberStableCoroutineScope()

    // exchangeRates are filtered to only include rates that involve the selected fiat currency
    val exchangeRates by remember {
      exchangeRateSyncer.exchangeRates
        .mapState(scope = scope) { rates ->
          rates.filter {
            it.toCurrency == fiatCurrency.textCode || it.fromCurrency == fiatCurrency.textCode
          }
        }
    }.collectAsState()

    SyncFiatBalanceEquivalentEffect(
      balance,
      fiatCurrency,
      isRefreshing,
      sessionState,
      exchangeRates.toImmutableList()
    ) {
      fiatBalance = it
      isRefreshing = false
    }

    return if (balance is LoadedValue && transactions is LoadedValue && unspentOutputs is LoadedValue) {
      FullAccountTransactionsLoadedData(
        balance = balance.value,
        transactions = transactions.value.toImmutableList(),
        unspentOutputs = unspentOutputs.value.toImmutableList(),
        fiatBalance = fiatBalance,
        syncFiatBalance = { isRefreshing = true },
        syncTransactions = {
          // Add a slight delay when a manual sync in requested in order to ensure
          // that the requested sync will include any new values
          delay(1.seconds)
          props.wallet.sync()
        }
      )
    } else {
      LoadingFullAccountTransactionsData
    }
  }

  @Composable
  private fun SyncFiatBalanceEquivalentEffect(
    balance: LoadableValue<BitcoinBalance>,
    fiatCurrency: FiatCurrency,
    isRefreshing: Boolean,
    sessionState: AppSessionState,
    exchangeRates: ImmutableList<ExchangeRate>,
    onFiatBalanceSynced: (FiatMoney) -> Unit,
  ) {
    if (balance !is LoadedValue) return

    // update the fiat balance if balance has changed or the selected fiat currency changes
    LaunchedEffect(
      "sync-fiat-equivalent-balance",
      balance.value.total,
      fiatCurrency,
      sessionState,
      exchangeRates
    ) {
      if (sessionState == AppSessionState.FOREGROUND) {
        val convertedFiatBalance =
          currencyConverter
            .convert(
              fromAmount = balance.value.total,
              toCurrency = fiatCurrency,
              atTime = null
            ).filterNotNull().firstOrNull() as? FiatMoney
            ?: FiatMoney.zero(fiatCurrency)

        onFiatBalanceSynced(convertedFiatBalance)
      }
    }

    // also manually update fiat balance when refreshing
    LaunchedEffect("manually-sync-fiat-balance", balance.value.total, fiatCurrency, isRefreshing) {
      if (isRefreshing) {
        val convertedFiatBalance =
          currencyConverter
            .convert(
              fromAmount = balance.value.total,
              toCurrency = fiatCurrency,
              atTime = null
            ).filterNotNull().firstOrNull() as? FiatMoney
            ?: FiatMoney.zero(fiatCurrency)

        onFiatBalanceSynced(convertedFiatBalance)
      }
    }
  }
}

/** Map a [StateFlow] to another [StateFlow] */
private fun <T, R> StateFlow<T>.mapState(
  scope: CoroutineScope,
  transform: (T) -> R,
): StateFlow<R> {
  return map { transform(it) }.stateIn(scope, Eagerly, transform(value))
}
