package build.wallet.statemachine.limit.picker

import androidx.compose.runtime.*
import build.wallet.configuration.MobilePayFiatConfigService
import build.wallet.feature.flags.MobilePayRevampFeatureFlag
import build.wallet.feature.isEnabled
import build.wallet.money.BitcoinMoney
import build.wallet.money.FiatMoney
import build.wallet.money.currency.BTC
import build.wallet.money.display.FiatCurrencyPreferenceRepository
import build.wallet.money.exchange.CurrencyConverter
import build.wallet.money.exchange.ExchangeRate
import build.wallet.money.exchange.ExchangeRateService
import build.wallet.money.formatter.MoneyDisplayFormatter
import build.wallet.statemachine.auth.ProofOfPossessionNfcProps
import build.wallet.statemachine.auth.ProofOfPossessionNfcStateMachine
import build.wallet.statemachine.auth.Request
import build.wallet.statemachine.core.ScreenModel
import build.wallet.statemachine.core.ScreenPresentationStyle
import build.wallet.statemachine.data.money.convertedOrZero
import build.wallet.statemachine.limit.ConfirmingWithHardwareErrorSheetModel
import build.wallet.statemachine.limit.SpendingLimitsCopy
import build.wallet.statemachine.limit.picker.SpendingLimitPickerUiState.ConfirmingWithHardwareUiState
import build.wallet.statemachine.limit.picker.SpendingLimitPickerUiState.PickingSpendingLimitUiState
import build.wallet.statemachine.money.calculator.MoneyCalculatorUiProps
import build.wallet.statemachine.money.calculator.MoneyCalculatorUiStateMachine
import build.wallet.ui.model.slider.AmountSliderModel
import build.wallet.ui.model.toolbar.ToolbarMiddleAccessoryModel
import build.wallet.ui.model.toolbar.ToolbarModel
import com.ionspin.kotlin.bignum.decimal.toBigDecimal
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList
import kotlin.math.abs
import kotlin.math.roundToInt

class SpendingLimitPickerUiStateMachineImpl(
  private val currencyConverter: CurrencyConverter,
  private val mobilePayFiatConfigService: MobilePayFiatConfigService,
  private val exchangeRateService: ExchangeRateService,
  private val moneyDisplayFormatter: MoneyDisplayFormatter,
  private val proofOfPossessionNfcStateMachine: ProofOfPossessionNfcStateMachine,
  private val fiatCurrencyPreferenceRepository: FiatCurrencyPreferenceRepository,
  private val moneyCalculatorUiStateMachine: MoneyCalculatorUiStateMachine,
  private val mobilePayRevampFeatureFlag: MobilePayRevampFeatureFlag,
) : SpendingLimitPickerUiStateMachine {
  @Composable
  override fun model(props: SpendingLimitPickerUiProps): ScreenModel {
    // Keep track of state values
    var fiatLimitValue by remember {
      mutableStateOf(props.initialLimit)
    }
    val mobilePayFiatConfig by mobilePayFiatConfigService.config.collectAsState()
    val isMobilePayRevampFeatureFlagEnabled = mobilePayRevampFeatureFlag.isEnabled()

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

    val sliderValue = fiatLimitValue.value.floatValue(exactRequired = false)
    val minimumFiatLimitAmount = mobilePayFiatConfig.minimumLimit.value.floatValue()
    val maximumFiatLimitAmount = mobilePayFiatConfig.maximumLimit.value.floatValue()
    val snapToleranceValues = mobilePayFiatConfig.snapValues

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
            snapToleranceValues
              .asSequence()
              .firstOrNull { entry ->
                abs(entry.key.value.intValue() - roundedValue.value.intValue()) <
                  entry.value.value.value
                    .intValue()
              }
          fiatLimitValue = snapValue?.key ?: roundedValue
        },
        isEnabled = isEnabled
      )

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
    val toolbarModel = if (isMobilePayRevampFeatureFlagEnabled) {
      ToolbarModel(leadingAccessory = props.retreat.leadingToolbarAccessory, middleAccessory = ToolbarMiddleAccessoryModel(title = "Set daily limit"))
    } else {
      ToolbarModel(leadingAccessory = props.retreat.leadingToolbarAccessory)
    }

    // Depending on feature flag, use different conditions for checking validity.
    val saveLimitEnabled by remember(fiatLimitValue, calculatorModel.primaryAmount) {
      derivedStateOf {
        when (isMobilePayRevampFeatureFlagEnabled) {
          true -> calculatorModel.primaryAmount.isPositive
          false -> fiatLimitValue.value > 0
        }
      }
    }

    fun confirmingWithHardwareState(isRevampOn: Boolean): ConfirmingWithHardwareUiState =
      when (isRevampOn) {
        true -> {
          // We **never** allow customers to switch primary input methods. Hence, we can be assured
          // that primary amount is always FiatMoney, and secondaryAmount is BitcoinMoney
          val fiatLimit = calculatorModel.primaryAmount as FiatMoney
          val btcLimit = calculatorModel.secondaryAmount as BitcoinMoney

          ConfirmingWithHardwareUiState(
            selectedFiatLimit = fiatLimit,
            selectedBtcLimit = btcLimit
          )
        }
        false -> ConfirmingWithHardwareUiState(
          selectedFiatLimit = fiatLimitValue,
          selectedBtcLimit = btcLimitValue
        )
      }

    fun entryMode(
      isRevampOn: Boolean,
      isEnabled: Boolean,
    ): EntryMode {
      return when (isRevampOn) {
        true -> EntryMode.Keypad(
          amountModel = calculatorModel.amountModel,
          keypadModel = calculatorModel.keypadModel
        )
        false -> EntryMode.Slider(
          sliderModel = amountSliderModel(isEnabled = isEnabled)
        )
      }
    }

    var uiState: SpendingLimitPickerUiState by remember {
      mutableStateOf(PickingSpendingLimitUiState)
    }

    return when (val state = uiState) {
      is PickingSpendingLimitUiState ->
        SpendingLimitPickerModel(
          onBack = props.retreat.onRetreat,
          toolbarModel = toolbarModel,
          entryMode = entryMode(isRevampOn = isMobilePayRevampFeatureFlagEnabled, isEnabled = true),
          setLimitButtonEnabled = saveLimitEnabled,
          setLimitButtonLoading = false,
          spendingLimitsCopy = SpendingLimitsCopy.get(isRevampOn = isMobilePayRevampFeatureFlagEnabled),
          onSetLimitClick = {
            uiState =
              confirmingWithHardwareState(isRevampOn = isMobilePayRevampFeatureFlagEnabled)
          }
        ).asModalScreen()

      is ConfirmingWithHardwareUiState ->
        ConfirmingWithHardwareModel(
          props = props,
          state = state,
          toolbarModel = toolbarModel,
          entryMode = entryMode(isRevampOn = isMobilePayRevampFeatureFlagEnabled, isEnabled = false),
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
        spendingLimitsCopy = SpendingLimitsCopy.get(isRevampOn = mobilePayRevampFeatureFlag.isEnabled()),
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
