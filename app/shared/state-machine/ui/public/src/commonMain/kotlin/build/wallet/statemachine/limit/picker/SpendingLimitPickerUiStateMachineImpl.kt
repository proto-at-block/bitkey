package build.wallet.statemachine.limit.picker

import androidx.compose.runtime.*
import build.wallet.di.ActivityScope
import build.wallet.di.BitkeyInject
import build.wallet.money.BitcoinMoney
import build.wallet.money.FiatMoney
import build.wallet.money.currency.BTC
import build.wallet.money.display.FiatCurrencyPreferenceRepository
import build.wallet.money.exchange.ExchangeRate
import build.wallet.money.exchange.ExchangeRateService
import build.wallet.statemachine.auth.ProofOfPossessionNfcProps
import build.wallet.statemachine.auth.ProofOfPossessionNfcStateMachine
import build.wallet.statemachine.auth.Request
import build.wallet.statemachine.core.ScreenModel
import build.wallet.statemachine.core.ScreenPresentationStyle
import build.wallet.statemachine.limit.ConfirmingWithHardwareErrorSheetModel
import build.wallet.statemachine.limit.picker.SpendingLimitPickerUiState.ConfirmingWithHardwareUiState
import build.wallet.statemachine.limit.picker.SpendingLimitPickerUiState.PickingSpendingLimitUiState
import build.wallet.statemachine.money.calculator.MoneyCalculatorUiProps
import build.wallet.statemachine.money.calculator.MoneyCalculatorUiStateMachine
import build.wallet.ui.model.toolbar.ToolbarMiddleAccessoryModel
import build.wallet.ui.model.toolbar.ToolbarModel
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList

@BitkeyInject(ActivityScope::class)
class SpendingLimitPickerUiStateMachineImpl(
  private val exchangeRateService: ExchangeRateService,
  private val proofOfPossessionNfcStateMachine: ProofOfPossessionNfcStateMachine,
  private val fiatCurrencyPreferenceRepository: FiatCurrencyPreferenceRepository,
  private val moneyCalculatorUiStateMachine: MoneyCalculatorUiStateMachine,
) : SpendingLimitPickerUiStateMachine {
  @Composable
  override fun model(props: SpendingLimitPickerUiProps): ScreenModel {
    // Keep track of state values
    val fiatLimitValue by remember {
      mutableStateOf(props.initialLimit)
    }

    // Computes calculator model we need to show keypad-based limit entry.
    // Unlike the slider, Money Calculator encapsulates both the keypad and amount display. Hence,
    // it requires to know the exchange rate upfront.
    val fiatCurrency by fiatCurrencyPreferenceRepository.fiatCurrencyPreference.collectAsState()
    val exchangeRates: ImmutableList<ExchangeRate> by remember {
      mutableStateOf(exchangeRateService.exchangeRates.value.toImmutableList())
    }

    val calculatorModel = moneyCalculatorUiStateMachine.model(
      props = MoneyCalculatorUiProps(
        inputAmountCurrency = fiatCurrency,
        secondaryDisplayAmountCurrency = BTC,
        initialAmountInInputCurrency = fiatLimitValue,
        exchangeRates = exchangeRates
      )
    )

    // Helper to build toolbar model
    val toolbarModel = ToolbarModel(
      leadingAccessory = props.retreat.leadingToolbarAccessory,
      middleAccessory = ToolbarMiddleAccessoryModel(title = "Set daily limit")
    )

    var uiState: SpendingLimitPickerUiState by remember {
      mutableStateOf(PickingSpendingLimitUiState)
    }

    return when (val state = uiState) {
      is PickingSpendingLimitUiState ->
        SpendingLimitPickerModel(
          onBack = props.retreat.onRetreat,
          toolbarModel = toolbarModel,
          entryMode = EntryMode.Keypad(
            amountModel = calculatorModel.amountModel,
            keypadModel = calculatorModel.keypadModel
          ),
          setLimitButtonEnabled = calculatorModel.primaryAmount.isPositive,
          setLimitButtonLoading = false,
          onSetLimitClick = {
            // We **never** allow customers to switch primary input methods. Hence, we can be assured
            // that primary amount is always FiatMoney, and secondaryAmount is BitcoinMoney
            val fiatLimit = calculatorModel.primaryAmount as FiatMoney
            val btcLimit = calculatorModel.secondaryAmount as BitcoinMoney

            uiState =
              ConfirmingWithHardwareUiState(
                selectedFiatLimit = fiatLimit,
                selectedBtcLimit = btcLimit
              )
          }
        ).asModalScreen()

      is ConfirmingWithHardwareUiState ->
        ConfirmingWithHardwareModel(
          props = props,
          state = state,
          toolbarModel = toolbarModel,
          entryMode = EntryMode.Keypad(
            amountModel = calculatorModel.amountModel,
            keypadModel = calculatorModel.keypadModel
          ),
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
    entryMode: EntryMode,
    toolbarModel: ToolbarModel,
    onBack: () -> Unit,
  ): ScreenModel {
    // Helper function for the picker body model that isn't functional / enabled,
    // but just displays while we show a loading or error states
    fun disabledSpendingLimitPickerModel(isLoading: Boolean) =
      SpendingLimitPickerModel(
        onBack = props.retreat.onRetreat,
        toolbarModel = toolbarModel,
        entryMode = entryMode,
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
          fullAccountId = props.account.accountId,
          fullAccountConfig = props.account.keybox.config,
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
