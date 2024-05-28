package build.wallet.statemachine.money.currency

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import build.wallet.analytics.events.EventTracker
import build.wallet.analytics.v1.Action
import build.wallet.compose.coroutines.rememberStableCoroutineScope
import build.wallet.feature.isEnabled
import build.wallet.inappsecurity.HideBalancePreference
import build.wallet.inappsecurity.InAppSecurityFeatureFlag
import build.wallet.money.FiatMoney
import build.wallet.money.currency.FiatCurrency
import build.wallet.money.currency.FiatCurrencyRepository
import build.wallet.money.display.BitcoinDisplayPreferenceRepository
import build.wallet.money.display.BitcoinDisplayUnit
import build.wallet.money.display.FiatCurrencyPreferenceRepository
import build.wallet.money.exchange.CurrencyConverter
import build.wallet.money.formatter.MoneyDisplayFormatter
import build.wallet.statemachine.core.ScreenModel
import build.wallet.statemachine.core.form.FormMainContentModel
import build.wallet.statemachine.money.currency.CurrencyPreferenceUiState.ShowingCurrencyFiatSelectionUiState
import build.wallet.statemachine.money.currency.CurrencyPreferenceUiState.ShowingCurrencyPreferenceUiState
import build.wallet.ui.model.list.ListItemPickerMenu
import com.github.michaelbull.result.onSuccess
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch

class CurrencyPreferenceUiStateMachineImpl(
  private val bitcoinDisplayPreferenceRepository: BitcoinDisplayPreferenceRepository,
  private val fiatCurrencyPreferenceRepository: FiatCurrencyPreferenceRepository,
  private val eventTracker: EventTracker,
  private val currencyConverter: CurrencyConverter,
  private val fiatCurrencyRepository: FiatCurrencyRepository,
  private val moneyDisplayFormatter: MoneyDisplayFormatter,
  private val inAppSecurityFeatureFlag: InAppSecurityFeatureFlag,
  private val hideBalancePreference: HideBalancePreference,
) : CurrencyPreferenceUiStateMachine {
  @Composable
  override fun model(props: CurrencyPreferenceProps): ScreenModel {
    var state: CurrencyPreferenceUiState by remember {
      mutableStateOf(ShowingCurrencyPreferenceUiState(isHideBalanceEnabled = false))
    }

    val selectedFiatCurrency by fiatCurrencyPreferenceRepository.fiatCurrencyPreference.collectAsState()

    val isHideBalanceEnabled by remember {
      hideBalancePreference.isEnabled
    }.onEach {
      when (val s = state) {
        is ShowingCurrencyPreferenceUiState -> state = s.copy(isHideBalanceEnabled = it)
        else -> {
          // no-op
        }
      }
    }
      .collectAsState(false)

    return when (state) {
      is ShowingCurrencyPreferenceUiState ->
        CurrencyPreferenceFormModel(
          props = props,
          selectedFiatCurrency = selectedFiatCurrency,
          isHideBalanceEnabled = isHideBalanceEnabled,
          onFiatCurrencyPreferenceClick = { state = ShowingCurrencyFiatSelectionUiState }
        )

      is ShowingCurrencyFiatSelectionUiState -> {
        val scope = rememberStableCoroutineScope()
        FiatCurrencyListFormModel(
          onClose = { state = ShowingCurrencyPreferenceUiState(isHideBalanceEnabled) },
          selectedCurrency = selectedFiatCurrency,
          currencyList = fiatCurrencyRepository.allFiatCurrencies.value,
          onCurrencySelection = { selectedCurrency ->
            scope.launch {
              fiatCurrencyPreferenceRepository.setFiatCurrencyPreference(selectedCurrency)
                .onSuccess {
                  eventTracker.track(Action.ACTION_APP_FIAT_CURRENCY_PREFERENCE_CHANGE)
                }
            }
            // Once a selection is made, we auto-close the list screen.
            state = ShowingCurrencyPreferenceUiState(isHideBalanceEnabled)
          }
        ).asModalScreen()
      }
    }
  }

  @Composable
  private fun CurrencyPreferenceFormModel(
    props: CurrencyPreferenceProps,
    selectedFiatCurrency: FiatCurrency,
    isHideBalanceEnabled: Boolean,
    onFiatCurrencyPreferenceClick: () -> Unit,
  ): ScreenModel {
    val selectedBitcoinUnit by bitcoinDisplayPreferenceRepository.bitcoinDisplayUnit.collectAsState()

    // Primary amount: fiat
    val convertedFiatAmount: FiatMoney =
      remember(props.btcDisplayAmount) {
        currencyConverter
          .convert(
            fromAmount = props.btcDisplayAmount,
            toCurrency = selectedFiatCurrency,
            atTime = null
          )
      }.collectAsState(null).value as? FiatMoney
        ?: FiatMoney.zero(selectedFiatCurrency) // If we're unable to convert to the currency, show zero amount
    val moneyHomeHeroPrimaryAmountString = moneyDisplayFormatter.format(convertedFiatAmount)

    // Secondary amount: bitcoin
    val moneyHomeHeroSecondaryAmountString =
      remember(props.btcDisplayAmount, selectedBitcoinUnit) {
        moneyDisplayFormatter
          .format(props.btcDisplayAmount)
      }

    var isShowingBitcoinUnitPicker by remember { mutableStateOf(false) }

    val scope = rememberStableCoroutineScope()
    val bitcoinDisplayPreferencePickerModel =
      remember(isShowingBitcoinUnitPicker, selectedBitcoinUnit) {
        ListItemPickerMenu(
          isShowing = isShowingBitcoinUnitPicker,
          selectedOption = selectedBitcoinUnit.displayText,
          options = BitcoinDisplayUnit.entries.map { it.displayText },
          onOptionSelected = { option ->
            scope.launch {
              val displayUnit = BitcoinDisplayUnit.entries
                .first { option == it.displayText }

              bitcoinDisplayPreferenceRepository
                .setBitcoinDisplayUnit(displayUnit)
                .onSuccess {
                  eventTracker.track(Action.ACTION_APP_BITCOIN_DISPLAY_PREFERENCE_CHANGE)
                }
              isShowingBitcoinUnitPicker = false
            }
          },
          onDismiss = {
            isShowingBitcoinUnitPicker = false
          }
        )
      }

    return CurrencyPreferenceFormModel(
      onBack = props.onBack,
      moneyHomeHero =
        FormMainContentModel.MoneyHomeHero(
          primaryAmount = moneyHomeHeroPrimaryAmountString,
          secondaryAmount = moneyHomeHeroSecondaryAmountString
        ),
      fiatCurrencyPreferenceString = selectedFiatCurrency.textCode.code,
      onFiatCurrencyPreferenceClick = onFiatCurrencyPreferenceClick,
      bitcoinDisplayPreferenceString = selectedBitcoinUnit.displayText,
      bitcoinDisplayPreferencePickerModel = bitcoinDisplayPreferencePickerModel,
      shouldShowHideBalance = inAppSecurityFeatureFlag.isEnabled(),
      isHideBalanceEnabled = isHideBalanceEnabled,
      onEnableHideBalanceChanged = { isEnabled ->
        scope.launch {
          hideBalancePreference.set(isEnabled)
        }
      },
      onBitcoinDisplayPreferenceClick = {
        isShowingBitcoinUnitPicker = true
      }
    ).asRootScreen()
  }
}

sealed interface CurrencyPreferenceUiState {
  data class ShowingCurrencyPreferenceUiState(
    val isHideBalanceEnabled: Boolean = false,
  ) : CurrencyPreferenceUiState

  data object ShowingCurrencyFiatSelectionUiState : CurrencyPreferenceUiState
}
