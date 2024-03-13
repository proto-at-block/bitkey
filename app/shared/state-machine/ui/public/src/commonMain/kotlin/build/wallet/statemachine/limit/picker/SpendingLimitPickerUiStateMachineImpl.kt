package build.wallet.statemachine.limit.picker

import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import build.wallet.configuration.FiatMobilePayConfiguration
import build.wallet.configuration.FiatMobilePayConfigurationRepository
import build.wallet.money.BitcoinMoney
import build.wallet.money.FiatMoney
import build.wallet.money.currency.BTC
import build.wallet.money.currency.FiatCurrency
import build.wallet.money.exchange.CurrencyConverter
import build.wallet.money.formatter.MoneyDisplayFormatter
import build.wallet.statemachine.auth.ProofOfPossessionNfcProps
import build.wallet.statemachine.auth.ProofOfPossessionNfcStateMachine
import build.wallet.statemachine.auth.Request
import build.wallet.statemachine.core.ScreenModel
import build.wallet.statemachine.core.ScreenPresentationStyle
import build.wallet.statemachine.data.money.convertedOrZero
import build.wallet.statemachine.limit.ConfirmingWithHardwareErrorSheetModel
import build.wallet.statemachine.limit.picker.SpendingLimitPickerUiState.ConfirmingWithHardwareUiState
import build.wallet.statemachine.limit.picker.SpendingLimitPickerUiState.PickingSpendingLimitUiState
import build.wallet.ui.model.slider.AmountSliderModel
import build.wallet.ui.model.toolbar.ToolbarModel
import com.ionspin.kotlin.bignum.decimal.toBigDecimal
import kotlin.math.abs
import kotlin.math.roundToInt

class SpendingLimitPickerUiStateMachineImpl(
  private val currencyConverter: CurrencyConverter,
  private val fiatMobilePayConfigurationRepository: FiatMobilePayConfigurationRepository,
  private val moneyDisplayFormatter: MoneyDisplayFormatter,
  private val proofOfPossessionNfcStateMachine: ProofOfPossessionNfcStateMachine,
) : SpendingLimitPickerUiStateMachine {
  @Composable
  override fun model(props: SpendingLimitPickerUiProps): ScreenModel {
    // Keep track of state values
    var fiatLimitValue by remember {
      mutableStateOf(props.initialLimit)
    }
    val fiatMobilePayConfiguration =
      fiatMobilePayConfigurationRepository.fiatMobilePayConfigurations
        .value[props.initialLimit.currency] ?: props.initialLimit.currency.defaultMobilePayConfiguration()

    val btcLimitValue =
      convertedOrZero(
        converter = currencyConverter,
        fromAmount = fiatLimitValue,
        toCurrency = BTC
      ) as BitcoinMoney

    val primaryLimitFormatted by remember(fiatLimitValue) {
      derivedStateOf {
        moneyDisplayFormatter.formatCompact(fiatLimitValue)
      }
    }

    val secondaryLimitFormatted by remember(btcLimitValue) {
      derivedStateOf {
        moneyDisplayFormatter.format(btcLimitValue)
      }
    }

    val saveLimitEnabled by remember(fiatLimitValue) {
      derivedStateOf {
        fiatLimitValue.value > 0
      }
    }

    val sliderValue = fiatLimitValue.value.floatValue(exactRequired = false)
    val minimumFiatLimitAmount = fiatMobilePayConfiguration.minimumLimit.value.floatValue()
    val maximumFiatLimitAmount = fiatMobilePayConfiguration.maximumLimit.value.floatValue()
    val snapToleranceValues = fiatMobilePayConfiguration.snapValues

    // Helper to build amount slider model from state values
    fun amountSliderModel(isEnabled: Boolean) =
      AmountSliderModel(
        primaryAmount = primaryLimitFormatted,
        secondaryAmount = secondaryLimitFormatted,
        value = sliderValue,
        valueRange = minimumFiatLimitAmount..maximumFiatLimitAmount,
        onValueUpdate = { newValue ->
          // round to nearest whole value to account for floating point calculation from slider UI
          val roundedValue =
            FiatMoney(fiatLimitValue.currency, newValue.roundToInt().toBigDecimal())
          val snapValue =
            snapToleranceValues.asSequence()
              .firstOrNull { entry ->
                abs(entry.key.value.intValue() - roundedValue.value.intValue()) < entry.value.value.value.intValue()
              }
          fiatLimitValue = snapValue?.key ?: roundedValue
        },
        isEnabled = isEnabled
      )

    // Helper to build toolbar model
    val toolbarModel =
      ToolbarModel(leadingAccessory = props.retreat.leadingToolbarAccessory)

    var uiState: SpendingLimitPickerUiState by remember {
      mutableStateOf(PickingSpendingLimitUiState)
    }

    return when (val state = uiState) {
      is PickingSpendingLimitUiState ->
        SpendingLimitPickerModel(
          onBack = props.retreat.onRetreat,
          toolbarModel = toolbarModel,
          limitSliderModel = amountSliderModel(isEnabled = true),
          setLimitButtonEnabled = saveLimitEnabled,
          setLimitButtonLoading = false,
          onSetLimitClick = {
            uiState =
              ConfirmingWithHardwareUiState(
                selectedFiatLimit = fiatLimitValue,
                selectedBtcLimit = btcLimitValue
              )
          }
        ).asModalScreen()

      is ConfirmingWithHardwareUiState ->
        ConfirmingWithHardwareModel(
          props = props,
          state = state,
          toolbarModel = toolbarModel,
          disabledAmountSliderModel = amountSliderModel(isEnabled = false),
          onBack = {
            uiState = PickingSpendingLimitUiState
          }
        )
    }
  }

  @Composable
  private fun ConfirmingWithHardwareModel(
    props: SpendingLimitPickerUiProps,
    state: ConfirmingWithHardwareUiState,
    toolbarModel: ToolbarModel,
    disabledAmountSliderModel: AmountSliderModel,
    onBack: () -> Unit,
  ): ScreenModel {
    // Helper function for the picker body model that isn't functional / enabled,
    // but just displays while we show a loading or error states
    fun disabledSpendingLimitPickerModel(isLoading: Boolean) =
      SpendingLimitPickerModel(
        onBack = props.retreat.onRetreat,
        toolbarModel = toolbarModel,
        limitSliderModel = disabledAmountSliderModel,
        setLimitButtonEnabled = true,
        setLimitButtonLoading = isLoading,
        onSetLimitClick = {}
      )

    return proofOfPossessionNfcStateMachine.model(
      props =
        ProofOfPossessionNfcProps(
          request =
            Request.HwKeyProof(
              onSuccess = { hwProofOfPossession ->
                props.onSaveLimit(
                  state.selectedFiatLimit,
                  state.selectedBtcLimit,
                  hwProofOfPossession
                )
              }
            ),
          fullAccountId = props.accountData.account.accountId,
          fullAccountConfig = props.accountData.account.keybox.config,
          onBack = onBack,
          screenPresentationStyle = ScreenPresentationStyle.Modal,
          onTokenRefresh = {
            // Provide a screen model to show while the token is being refreshed.
            // We want this to be the same as [PickingSpendingLimitUiState]
            // but with the button in a loading state
            disabledSpendingLimitPickerModel(isLoading = true).asModalScreen()
          },
          onTokenRefreshError = { isConnectivityError, _ ->
            // Provide a screen model to show if the token refresh results in an error.
            // We want this to be the same as [PickingSpendingLimitUiState]
            // but with the error bottom sheet showing
            ScreenModel(
              body = disabledSpendingLimitPickerModel(isLoading = true),
              bottomSheetModel =
                ConfirmingWithHardwareErrorSheetModel(
                  isConnectivityError = isConnectivityError,
                  onClosed = onBack
                ),
              presentationStyle = ScreenPresentationStyle.Modal
            )
          }
        )
    )
  }
}

sealed interface SpendingLimitPickerUiState {
  /**
   * Customer is using the slider UI to select a limit amount
   */
  data object PickingSpendingLimitUiState : SpendingLimitPickerUiState

  /**
   * After the customer selects a limit amount, we need to verify proof of HW possession.
   *
   * This involves 2 steps:
   * - A server request to f8e to load auth tokens
   * - NFC communication with the hardware
   *
   * While the server request loads we want to show the picker UI with the button in a
   * loading state, which is why this state is part of this state machine.
   *
   * We purposely snapshot these limits in this state data class so they're not updated
   * any more (like based on exchange rates) while the proof of possession is taking place.
   *
   * @property selectedBtcLimit The limit the user would like to update to in btc
   * @property selectedFiatLimit The limit the user would like to update to in fiat
   */
  data class ConfirmingWithHardwareUiState(
    val selectedFiatLimit: FiatMoney,
    val selectedBtcLimit: BitcoinMoney,
  ) : SpendingLimitPickerUiState
}

// Use a config with a max limit value of 200 ($200) as a default in the unexpected case we don't
// have a stored default config or config from the server for the limit fiat currency
private fun FiatCurrency.defaultMobilePayConfiguration() =
  FiatMobilePayConfiguration(
    minimumLimit = FiatMoney(this, 0.toBigDecimal()),
    maximumLimit = FiatMoney(this, 200.toBigDecimal()),
    snapValues = emptyMap()
  )
