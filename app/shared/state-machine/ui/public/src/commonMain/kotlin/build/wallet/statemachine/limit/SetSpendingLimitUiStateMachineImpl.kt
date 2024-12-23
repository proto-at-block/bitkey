package build.wallet.statemachine.limit

import androidx.compose.runtime.*
import build.wallet.analytics.events.screen.id.MobilePayEventTrackerScreenId
import build.wallet.di.ActivityScope
import build.wallet.di.BitkeyInject
import build.wallet.f8e.auth.HwFactorProofOfPossession
import build.wallet.limit.MobilePayService
import build.wallet.limit.SpendingLimit
import build.wallet.money.BitcoinMoney
import build.wallet.money.FiatMoney
import build.wallet.money.display.FiatCurrencyPreferenceRepository
import build.wallet.money.formatter.MoneyDisplayFormatter
import build.wallet.statemachine.core.*
import build.wallet.statemachine.core.RetreatStyle.Close
import build.wallet.statemachine.limit.SpendingLimitUiState.*
import build.wallet.statemachine.limit.picker.SpendingLimitPickerUiProps
import build.wallet.statemachine.limit.picker.SpendingLimitPickerUiStateMachine
import build.wallet.time.TimeZoneProvider
import com.github.michaelbull.result.Result
import com.github.michaelbull.result.onFailure
import com.github.michaelbull.result.onSuccess

@BitkeyInject(ActivityScope::class)
class SetSpendingLimitUiStateMachineImpl(
  private val spendingLimitPickerUiStateMachine: SpendingLimitPickerUiStateMachine,
  private val timeZoneProvider: TimeZoneProvider,
  private val moneyDisplayFormatter: MoneyDisplayFormatter,
  private val fiatCurrencyPreferenceRepository: FiatCurrencyPreferenceRepository,
  private val mobilePayService: MobilePayService,
) : SetSpendingLimitUiStateMachine {
  @Composable
  override fun model(props: SpendingLimitProps): ScreenModel {
    var uiState: SpendingLimitUiState by remember {
      mutableStateOf(PickingAndConfirmingSpendingLimitUiState(props.currentSpendingLimit))
    }

    return when (val state = uiState) {
      is PickingAndConfirmingSpendingLimitUiState ->
        PickingAndConfirmingSpendingLimitScreenModel(
          props = props,
          state = state,
          onLimitPickedAndConfirmed = { fiatLimit, btcLimit, spendingLimit, hwProofOfPossession ->
            uiState =
              SavingLimitUiState(
                selectedFiatLimit = fiatLimit,
                selectedBtcLimit = btcLimit,
                spendingLimit = spendingLimit,
                hwFactorProofOfPossession = hwProofOfPossession
              )
          }
        )

      is ReceivedSavingErrorUiState ->
        FailedToSetLimitScreenModel(
          onGoBack = {
            uiState = PickingAndConfirmingSpendingLimitUiState(props.currentSpendingLimit)
          }
        )

      is SavingLimitUiState ->
        SavingLimitScreenModel(
          props = props,
          state = state,
          onResult = { result ->
            result
              .onSuccess {
                uiState =
                  SpendingLimitIsSetUiState(
                    selectedBtcLimit = state.selectedBtcLimit,
                    selectedFiatLimit = state.selectedFiatLimit,
                    spendingLimit = state.spendingLimit
                  )
              }
              .onFailure {
                uiState = ReceivedSavingErrorUiState
              }
          }
        )

      is SpendingLimitIsSetUiState -> SpendingLimitIsSetScreenModel(props, state)
    }
  }

  @Composable
  private fun SavingLimitScreenModel(
    props: SpendingLimitProps,
    state: SavingLimitUiState,
    onResult: (Result<Unit, Error>) -> Unit,
  ): ScreenModel {
    SaveLimitEffect(props, state, onResult)

    return LoadingBodyModel(
      id = MobilePayEventTrackerScreenId.MOBILE_PAY_LIMIT_UPDATE_LOADING,
      message = "Saving Limit..."
    ).asModalScreen()
  }

  @Composable
  private fun SaveLimitEffect(
    props: SpendingLimitProps,
    state: SavingLimitUiState,
    onResult: (Result<Unit, Error>) -> Unit,
  ) {
    LaunchedEffect("saving-spending-limit") {
      mobilePayService.setLimit(
        account = props.account,
        spendingLimit = state.spendingLimit,
        hwFactorProofOfPossession = state.hwFactorProofOfPossession
      )
        .apply(onResult)
    }
  }

  @Composable
  private fun PickingAndConfirmingSpendingLimitScreenModel(
    props: SpendingLimitProps,
    state: PickingAndConfirmingSpendingLimitUiState,
    onLimitPickedAndConfirmed: (
      FiatMoney,
      BitcoinMoney,
      SpendingLimit,
      HwFactorProofOfPossession,
    ) -> Unit,
  ): ScreenModel {
    val fiatCurrency by fiatCurrencyPreferenceRepository.fiatCurrencyPreference.collectAsState()
    return spendingLimitPickerUiStateMachine.model(
      props = SpendingLimitPickerUiProps(
        account = props.account,
        initialLimit = state.selectedFiatLimit ?: FiatMoney.zero(fiatCurrency),
        retreat = Retreat(
          style = Close,
          onRetreat = props.onClose
        ),
        onSaveLimit = { fiatLimit, btcLimit, hwFactorProofOfPossession ->
          val spendingLimit = SpendingLimit(
            active = true,
            amount = fiatLimit,
            timezone = timeZoneProvider.current()
          )
          onLimitPickedAndConfirmed(fiatLimit, btcLimit, spendingLimit, hwFactorProofOfPossession)
        }
      )
    )
  }

  @Composable
  private fun SpendingLimitIsSetScreenModel(
    props: SpendingLimitProps,
    state: SpendingLimitIsSetUiState,
  ): ScreenModel {
    val fiatString = moneyDisplayFormatter.format(state.selectedFiatLimit)
    val btcString = moneyDisplayFormatter.format(state.selectedBtcLimit)
    return SuccessBodyModel(
      id = MobilePayEventTrackerScreenId.MOBILE_PAY_LIMIT_UPDATE_SUCCESS,
      title = "You're all set.",
      message =
        "Now you can spend up to $fiatString " +
          "($btcString) per day with just your phone.",
      primaryButtonModel = ButtonDataModel(
        text = "Done",
        onClick = { props.onSetLimit(state.spendingLimit) }
      )
    ).asModalScreen()
  }

  @Composable
  private fun FailedToSetLimitScreenModel(onGoBack: () -> Unit) =
    ErrorFormBodyModel(
      title = "We were unable to set your spending limit.",
      subline = "Make sure you are connected to the internet or try again later.",
      primaryButton = ButtonDataModel(text = "Go Back", onClick = onGoBack),
      eventTrackerScreenId = MobilePayEventTrackerScreenId.MOBILE_PAY_LIMIT_UPDATE_FAILURE
    ).asModalScreen()
}

sealed interface SpendingLimitUiState {
  /**
   * Displays the spending limit picker UI allowing the user to select the limit amount.
   * In this step the user also confirms the limit with hardware.
   */
  data class PickingAndConfirmingSpendingLimitUiState(
    val selectedFiatLimit: FiatMoney?,
  ) : SpendingLimitUiState

  /**
   * Saving limit, shows a loading screen
   *
   * @property selectedBtcLimit The limit the user would like to update to in btc
   * @property selectedFiatLimit The limit the user would like to update to in fiat
   * @property spendingLimit - The initialized spending limit
   * @property hwFactorProofOfPossession - The signature received from the hardware wallet to verify data
   */
  data class SavingLimitUiState(
    val selectedBtcLimit: BitcoinMoney,
    val selectedFiatLimit: FiatMoney,
    val spendingLimit: SpendingLimit,
    val hwFactorProofOfPossession: HwFactorProofOfPossession,
  ) : SpendingLimitUiState

  /**
   * Displays once the spending limit is successfully saved
   *
   * @property selectedBtcLimit The limit the user would like to update to in btc
   * @property selectedFiatLimit The limit the user would like to update to in fiat
   */
  data class SpendingLimitIsSetUiState(
    val selectedBtcLimit: BitcoinMoney,
    val selectedFiatLimit: FiatMoney,
    val spendingLimit: SpendingLimit,
  ) : SpendingLimitUiState

  /**
   * Received saving error while saving to the backend
   */
  data object ReceivedSavingErrorUiState : SpendingLimitUiState
}
