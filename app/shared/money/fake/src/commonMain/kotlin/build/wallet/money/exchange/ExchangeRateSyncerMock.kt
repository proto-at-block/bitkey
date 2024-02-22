package build.wallet.money.exchange

import app.cash.turbine.Turbine
import app.cash.turbine.plusAssign
import build.wallet.money.currency.code.IsoCurrencyTextCode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.datetime.Clock
import kotlin.time.Duration

class ExchangeRateSyncerMock(
  turbine: (String) -> Turbine<Any>,
  clock: Clock = Clock.System,
) : ExchangeRateSyncer {
  val startSyncerCalls = turbine("sync rates calls")
  var internalExchangeRates =
    MutableStateFlow(
      listOf(
        ExchangeRate(
          IsoCurrencyTextCode("BTC"),
          IsoCurrencyTextCode("USD"),
          33333.0,
          timeRetrieved = clock.now()
        )
      )
    )

  override val exchangeRates: StateFlow<List<ExchangeRate>>
    get() = internalExchangeRates

  override fun launchSync(
    scope: CoroutineScope,
    syncFrequency: Duration,
  ) {
    startSyncerCalls += syncFrequency
  }
}
