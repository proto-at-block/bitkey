package build.wallet.statemachine.data.keybox.transactions

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.remember
import build.wallet.LoadableValue.InitialLoading
import build.wallet.LoadableValue.LoadedValue
import build.wallet.map
import build.wallet.statemachine.data.keybox.transactions.FullAccountTransactionsData.FullAccountTransactionsLoadedData
import build.wallet.statemachine.data.keybox.transactions.FullAccountTransactionsData.LoadingFullAccountTransactionsData
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.map
import kotlin.time.Duration.Companion.seconds

class FullAccountTransactionsDataStateMachineImpl : FullAccountTransactionsDataStateMachine {
  @Composable
  override fun model(props: FullAccountTransactionsDataProps): FullAccountTransactionsData {
    LaunchedEffect("sync-wallet", props.wallet) {
      // Make sure to initialize the balance and transactions before the sync occurs so that
      // the call to sync doesn't block fetching the balance and transactions
      props.wallet.initializeBalanceAndTransactions()
      props.wallet.launchPeriodicSync(
        scope = this,
        interval = 10.seconds
      )
    }

    val balance =
      remember(props.wallet) {
        props.wallet.balance()
      }.collectAsState(InitialLoading).value

    val transactions =
      remember(props.wallet) {
        props.wallet.transactions().map { value -> value.map { it.toImmutableList() } }
      }.collectAsState(InitialLoading).value

    return if (balance is LoadedValue && transactions is LoadedValue) {
      FullAccountTransactionsLoadedData(
        balance = balance.value,
        transactions = transactions.value,
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
}
