package build.wallet.statemachine.limit.picker

import androidx.compose.runtime.*
import bitkey.account.HardwareType
import bitkey.privilegedactions.ActionProofError
import bitkey.privilegedactions.ActionProofService
import build.wallet.di.ActivityScope
import build.wallet.di.BitkeyInject
import build.wallet.f8e.actionproof.FormatValueRequest
import build.wallet.ktor.result.HttpError
import build.wallet.logging.logFailure
import build.wallet.money.BitcoinMoney
import build.wallet.money.FiatMoney
import build.wallet.money.currency.BTC
import build.wallet.money.display.FiatCurrencyPreferenceRepository
import build.wallet.money.exchange.ExchangeRate
import build.wallet.money.exchange.ExchangeRateService
import build.wallet.platform.settings.Locale
import build.wallet.platform.settings.LocaleProvider
import build.wallet.statemachine.auth.ActionProofType
import build.wallet.statemachine.auth.HardwareAuthUiProps
import build.wallet.statemachine.auth.HardwareAuthUiStateMachine
import build.wallet.statemachine.core.AppSegment
import build.wallet.statemachine.core.ScreenModel
import build.wallet.statemachine.core.ScreenPresentationStyle
import build.wallet.statemachine.keypad.KeypadModel
import build.wallet.statemachine.limit.ConfirmingWithHardwareErrorSheetModel
import build.wallet.statemachine.limit.picker.SpendingLimitPickerUiState.*
import build.wallet.statemachine.money.amount.MoneyAmountEntryModel
import build.wallet.statemachine.money.calculator.MoneyCalculatorUiProps
import build.wallet.statemachine.money.calculator.MoneyCalculatorUiStateMachine
import build.wallet.ui.model.toolbar.ToolbarMiddleAccessoryModel
import build.wallet.ui.model.toolbar.ToolbarModel
import com.github.michaelbull.result.onFailure
import com.github.michaelbull.result.onSuccess
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList

@BitkeyInject(ActivityScope::class)
class SpendingLimitPickerUiStateMachineImpl(
  private val exchangeRateService: ExchangeRateService,
  private val hardwareAuthUiStateMachine: HardwareAuthUiStateMachine,
  private val fiatCurrencyPreferenceRepository: FiatCurrencyPreferenceRepository,
  private val moneyCalculatorUiStateMachine: MoneyCalculatorUiStateMachine,
  private val actionProofService: ActionProofService,
  private val localeProvider: LocaleProvider,
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
          amountModel = calculatorModel.amountModel,
          keypadModel = calculatorModel.keypadModel,
          setLimitButtonEnabled = calculatorModel.primaryAmount.isPositive,
          setLimitButtonLoading = false,
          onSetLimitClick = {
            // We **never** allow customers to switch primary input methods. Hence, we can be assured
            // that primary amount is always FiatMoney, and secondaryAmount is BitcoinMoney
            val fiatLimit = calculatorModel.primaryAmount as FiatMoney
            val btcLimit = calculatorModel.secondaryAmount as BitcoinMoney

            uiState = when (props.account.config.hardwareType) {
              // W1 doesn't use ActionProof values, skip the format endpoint
              HardwareType.W1 ->
                ConfirmingWithHardwareUiState(
                  selectedFiatLimit = fiatLimit,
                  selectedBtcLimit = btcLimit,
                  actionProofValue = fiatLimit.fractionalUnitValue.toString(),
                  locale = localeProvider.currentLocale()
                )
              // W3 needs a server-formatted display value for ActionProof signing
              HardwareType.W3 ->
                FormattingActionProofValueUiState(
                  selectedFiatLimit = fiatLimit,
                  selectedBtcLimit = btcLimit
                )
            }
          }
        ).asModalScreen()

      is FormattingActionProofValueUiState ->
        FormattingActionProofValueModel(
          state = state,
          toolbarModel = toolbarModel,
          amountModel = calculatorModel.amountModel,
          keypadModel = calculatorModel.keypadModel,
          onFormatted = { formattedValue, locale ->
            uiState =
              ConfirmingWithHardwareUiState(
                selectedFiatLimit = state.selectedFiatLimit,
                selectedBtcLimit = state.selectedBtcLimit,
                actionProofValue = formattedValue,
                locale = locale
              )
          },
          onFormatError = { error ->
            uiState = FormatErrorUiState(error)
          },
          onBack = {
            uiState = PickingSpendingLimitUiState
          }
        )

      is FormatErrorUiState ->
        ScreenModel(
          body = SpendingLimitPickerModel(
            onBack = props.retreat.onRetreat,
            toolbarModel = toolbarModel,
            amountModel = calculatorModel.amountModel,
            keypadModel = calculatorModel.keypadModel,
            setLimitButtonEnabled = false,
            setLimitButtonLoading = false,
            onSetLimitClick = {}
          ),
          bottomSheetModel = ConfirmingWithHardwareErrorSheetModel(
            isConnectivityError = state.error.isFormatValueConnectivityError,
            error = state.error,
            onClosed = { uiState = PickingSpendingLimitUiState }
          ),
          presentationStyle = ScreenPresentationStyle.Modal
        )

      is ConfirmingWithHardwareUiState ->
        ConfirmingWithHardwareModel(
          props = props,
          state = state,
          toolbarModel = toolbarModel,
          amountModel = calculatorModel.amountModel,
          keypadModel = calculatorModel.keypadModel,
          onBack = {
            uiState = PickingSpendingLimitUiState
          }
        )
    }
  }

  @Composable
  private fun FormattingActionProofValueModel(
    state: FormattingActionProofValueUiState,
    amountModel: MoneyAmountEntryModel,
    keypadModel: KeypadModel,
    toolbarModel: ToolbarModel,
    onFormatted: (formattedValue: String, locale: Locale) -> Unit,
    onFormatError: (Throwable) -> Unit,
    onBack: () -> Unit,
  ): ScreenModel {
    val locale = remember {
      localeProvider.currentLocale()
    }

    LaunchedEffect("formatting-value", state.selectedFiatLimit) {
      actionProofService.formatDisplayValue(
        request = FormatValueRequest.SetSpendWithoutHardware(
          amount = state.selectedFiatLimit.fractionalUnitValue.ulongValue(exactRequired = true),
          currencyCode = state.selectedFiatLimit.currency.textCode,
          locale = locale.toBcp47()
        )
      )
        .onSuccess { formattedValue ->
          onFormatted(formattedValue, locale)
        }
        .logFailure { "Failed to format spending limit display value" }
        .onFailure { error ->
          onFormatError(error)
        }
    }

    // Show the picker with the button in a loading state while formatting
    return SpendingLimitPickerModel(
      onBack = onBack,
      toolbarModel = toolbarModel,
      amountModel = amountModel,
      keypadModel = keypadModel,
      setLimitButtonEnabled = true,
      setLimitButtonLoading = true,
      onSetLimitClick = {}
    ).asModalScreen()
  }

  @Composable
  private fun ConfirmingWithHardwareModel(
    props: SpendingLimitPickerUiProps,
    state: ConfirmingWithHardwareUiState,
    amountModel: MoneyAmountEntryModel,
    keypadModel: KeypadModel,
    toolbarModel: ToolbarModel,
    onBack: () -> Unit,
  ): ScreenModel {
    // Helper function for the picker body model that isn't functional / enabled,
    // but just displays while we show a loading or error states
    fun disabledSpendingLimitPickerModel(isLoading: Boolean) =
      SpendingLimitPickerModel(
        onBack = props.retreat.onRetreat,
        toolbarModel = toolbarModel,
        amountModel = amountModel,
        keypadModel = keypadModel,
        setLimitButtonEnabled = true,
        setLimitButtonLoading = isLoading,
        onSetLimitClick = {}
      )

    return hardwareAuthUiStateMachine.model(
      props =
        HardwareAuthUiProps(
          account = props.account,
          actionProofType = ActionProofType.SetMobilePayLimit(
            limit = state.actionProofValue,
            currency = state.selectedFiatLimit.currency.textCode.code
          ),
          segment = SpendingLimitPickerAppSegment,
          actionDescription = "Setting mobile pay limit",
          screenPresentationStyle = ScreenPresentationStyle.Modal,
          onSuccess = { proof ->
            props.onSaveLimit(
              state.selectedFiatLimit,
              state.selectedBtcLimit,
              proof,
              state.locale
            )
          },
          onBack = onBack,
          onTokenRefresh = {
            // Provide a screen model to show while the token is being refreshed.
            // We want this to be the same as [PickingSpendingLimitUiState]
            // but with the button in a loading state
            disabledSpendingLimitPickerModel(isLoading = true).asModalScreen()
          },
          onTokenRefreshError = { isConnectivityError, error, _ ->
            // Provide a screen model to show if the token refresh results in an error.
            // We want this to be the same as [PickingSpendingLimitUiState]
            // but with the error bottom sheet showing
            ScreenModel(
              body = disabledSpendingLimitPickerModel(isLoading = true),
              bottomSheetModel =
                ConfirmingWithHardwareErrorSheetModel(
                  isConnectivityError = isConnectivityError,
                  error = error,
                  onClosed = onBack
                ),
              presentationStyle = ScreenPresentationStyle.Modal
            )
          }
        )
    )
  }
}

/**
 * App segment for spending limit picker error tracking.
 */
private object SpendingLimitPickerAppSegment : AppSegment {
  override val id: String = "SpendingLimitPicker"
}

sealed interface SpendingLimitPickerUiState {
  /**
   * Customer is using the slider UI to select a limit amount
   */
  data object PickingSpendingLimitUiState : SpendingLimitPickerUiState

  /**
   * Calling the server to get a formatted display value for the selected limit.
   * Shows the picker UI with the button in a loading state.
   */
  data class FormattingActionProofValueUiState(
    val selectedFiatLimit: FiatMoney,
    val selectedBtcLimit: BitcoinMoney,
  ) : SpendingLimitPickerUiState

  /**
   * The server format request failed. Shows an error sheet over the picker UI.
   */
  data class FormatErrorUiState(
    val error: Throwable,
  ) : SpendingLimitPickerUiState

  /**
   * After the customer selects a limit amount and the action proof value is obtained,
   * we need to verify proof of HW possession.
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
   * @property actionProofValue Value for action proof signing — server-formatted for W3
   *   (e.g. "50.00 USD"), raw fractional unit amount for W1 (e.g. "5000")
   * @property locale BCP 47 locale used for formatting, passed through to the save request
   */
  data class ConfirmingWithHardwareUiState(
    val selectedFiatLimit: FiatMoney,
    val selectedBtcLimit: BitcoinMoney,
    val actionProofValue: String,
    val locale: Locale,
  ) : SpendingLimitPickerUiState
}

private val Throwable.isFormatValueConnectivityError: Boolean
  get() = this is ActionProofError.F8eError && cause is HttpError.NetworkError
