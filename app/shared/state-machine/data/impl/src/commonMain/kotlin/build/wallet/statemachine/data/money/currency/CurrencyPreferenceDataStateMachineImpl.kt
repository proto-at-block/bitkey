package build.wallet.statemachine.data.money.currency

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.remember
import build.wallet.analytics.events.EventTracker
import build.wallet.analytics.v1.Action
import build.wallet.compose.coroutines.rememberStableCoroutineScope
import build.wallet.money.currency.FiatCurrency
import build.wallet.money.display.BitcoinDisplayPreferenceRepository
import build.wallet.money.display.BitcoinDisplayUnit
import build.wallet.money.display.CurrencyPreferenceData
import build.wallet.money.display.FiatCurrencyPreferenceRepository
import kotlinx.coroutines.launch

class CurrencyPreferenceDataStateMachineImpl(
  private val bitcoinDisplayPreferenceRepository: BitcoinDisplayPreferenceRepository,
  private val eventTracker: EventTracker,
  private val fiatCurrencyPreferenceRepository: FiatCurrencyPreferenceRepository,
) : CurrencyPreferenceDataStateMachine {
  @Composable
  override fun model(props: Unit): CurrencyPreferenceData {
    val scope = rememberStableCoroutineScope()

    return CurrencyPreferenceData(
      bitcoinDisplayUnitPreference = rememberBitcoinDisplayPreference(),
      setBitcoinDisplayUnitPreference = { displayUnit ->
        scope.launch {
          bitcoinDisplayPreferenceRepository.setBitcoinDisplayUnit(displayUnit)
          eventTracker.track(Action.ACTION_APP_BITCOIN_DISPLAY_PREFERENCE_CHANGE)
        }
      },
      fiatCurrencyPreference = rememberFiatCurrencyPreference(),
      setFiatCurrencyPreference = { currency ->
        scope.launch {
          fiatCurrencyPreferenceRepository.setFiatCurrencyPreference(currency)
          eventTracker.track(Action.ACTION_APP_FIAT_CURRENCY_PREFERENCE_CHANGE)
        }
      }
    )
  }

  @Composable
  private fun rememberBitcoinDisplayPreference(): BitcoinDisplayUnit {
    return remember {
      bitcoinDisplayPreferenceRepository.bitcoinDisplayUnit
    }.collectAsState().value
  }

  @Composable
  private fun rememberFiatCurrencyPreference(): FiatCurrency {
    return remember {
      fiatCurrencyPreferenceRepository.fiatCurrencyPreference
    }.collectAsState().value ?: remember {
      fiatCurrencyPreferenceRepository.defaultFiatCurrency
    }.collectAsState().value
  }
}
