package build.wallet.statemachine.recovery.sweep

import androidx.compose.runtime.*
import build.wallet.analytics.events.screen.context.NfcEventTrackerScreenIdContext
import build.wallet.analytics.events.screen.id.DelayNotifyRecoveryEventTrackerScreenId
import build.wallet.analytics.events.screen.id.HardwareRecoveryEventTrackerScreenId
import build.wallet.analytics.events.screen.id.InactiveWalletSweepEventTrackerScreenId
import build.wallet.bitkey.factor.PhysicalFactor.App
import build.wallet.bitkey.factor.PhysicalFactor.Hardware
import build.wallet.di.ActivityScope
import build.wallet.di.BitkeyInject
import build.wallet.money.display.FiatCurrencyPreferenceRepository
import build.wallet.platform.web.InAppBrowserNavigator
import build.wallet.statemachine.core.ErrorData
import build.wallet.statemachine.core.InAppBrowserModel
import build.wallet.statemachine.core.ScreenModel
import build.wallet.statemachine.data.recovery.sweep.SweepData
import build.wallet.statemachine.data.recovery.sweep.SweepData.*
import build.wallet.statemachine.data.recovery.sweep.SweepDataProps
import build.wallet.statemachine.data.recovery.sweep.SweepDataStateMachine
import build.wallet.statemachine.money.amount.MoneyAmountUiProps
import build.wallet.statemachine.money.amount.MoneyAmountUiStateMachine
import build.wallet.statemachine.nfc.NfcSessionUIStateMachine
import build.wallet.statemachine.nfc.NfcSessionUIStateMachineProps
import build.wallet.statemachine.recovery.RecoverySegment

@BitkeyInject(ActivityScope::class)
class SweepUiStateMachineImpl(
  private val nfcSessionUIStateMachine: NfcSessionUIStateMachine,
  private val moneyAmountUiStateMachine: MoneyAmountUiStateMachine,
  private val fiatCurrencyPreferenceRepository: FiatCurrencyPreferenceRepository,
  private val sweepDataStateMachine: SweepDataStateMachine,
  private val inAppBrowserNavigator: InAppBrowserNavigator,
) : SweepUiStateMachine {
  @Composable
  override fun model(props: SweepUiProps): ScreenModel {
    var screenState: ScreenState by remember { mutableStateOf(ScreenState.ShowingSweepState) }
    val sweepData = sweepDataStateMachine.model(
      SweepDataProps(
        keybox = props.keybox,
        onSuccess = props.onSuccess
      )
    )

    return when (val uiState = screenState) {
      ScreenState.ShowingSweepState -> getSweepScreen(props, sweepData, setState = { screenState = it })
      ScreenState.ShowingHelpText -> sweepInactiveHelpModel(
        id = InactiveWalletSweepEventTrackerScreenId.INACTIVE_WALLET_HELP,
        presentationStyle = props.presentationStyle,
        onLearnMore = {
          screenState = ScreenState.ShowingLearnMore("https://support.bitkey.world/hc/en-us/articles/28019865146516-How-do-I-access-funds-sent-to-a-previously-created-Bitkey-address")
        },
        onBack = { screenState = ScreenState.ShowingSweepState }
      )
      is ScreenState.ShowingLearnMore -> {
        InAppBrowserModel(
          open = {
            inAppBrowserNavigator.open(
              url = uiState.urlString,
              onClose = { screenState = ScreenState.ShowingHelpText }
            )
          }
        ).asModalScreen()
      }
    }
  }

  @Composable
  private fun getSweepScreen(
    props: SweepUiProps,
    sweepData: SweepData,
    setState: (ScreenState) -> Unit,
  ): ScreenModel {
    // TODO: Add Hardware Proof of Possession state machine if GetAccountKeysets
    //   endpoint ends up requiring it.
    return when (sweepData) {
      /** Show spinner while we wait for PSBTs to be generated */
      is GeneratingPsbtsData ->
        generatingPsbtsBodyModel(
          id = when (props.recoveredFactor) {
            App -> DelayNotifyRecoveryEventTrackerScreenId.LOST_APP_DELAY_NOTIFY_SWEEP_GENERATING_PSBTS
            Hardware -> HardwareRecoveryEventTrackerScreenId.LOST_HW_DELAY_NOTIFY_SWEEP_GENERATING_PSBTS
            null -> InactiveWalletSweepEventTrackerScreenId.INACTIVE_WALLET_SWEEP_GENERATING_PSBTS
          },
          onBack = props.onExit,
          presentationStyle = props.presentationStyle
        )

      /** Terminal error state: PSBT generation failed */
      is GeneratePsbtsFailedData ->
        generatePsbtsFailedScreenModel(
          id = when (props.recoveredFactor) {
            App -> DelayNotifyRecoveryEventTrackerScreenId.LOST_APP_DELAY_NOTIFY_SWEEP_GENERATE_PSBTS_ERROR
            Hardware -> HardwareRecoveryEventTrackerScreenId.LOST_HW_DELAY_NOTIFY_SWEEP_GENERATE_PSBTS_ERROR
            null -> InactiveWalletSweepEventTrackerScreenId.INACTIVE_WALLET_SWEEP_GENERATE_PSBTS_ERROR
          },
          onPrimaryButtonClick = props.onExit,
          presentationStyle = props.presentationStyle
        )

      is NoFundsFoundData ->
        zeroBalancePrompt(
          id = when (props.recoveredFactor) {
            App -> DelayNotifyRecoveryEventTrackerScreenId.LOST_APP_DELAY_NOTIFY_SWEEP_ZERO_BALANCE
            Hardware -> HardwareRecoveryEventTrackerScreenId.LOST_HW_DELAY_NOTIFY_SWEEP_ZERO_BALANCE
            null -> InactiveWalletSweepEventTrackerScreenId.INACTIVE_WALLET_SWEEP_ZERO_BALANCE
          },
          onDone = sweepData.proceed,
          presentationStyle = props.presentationStyle
        )

      /** PSBTs have been generated. Prompt to continue to sign + broadcast. */
      is PsbtsGeneratedData -> {
        var showingNetworkFeesInfo by remember { mutableStateOf(false) }
        val fiatCurrency by fiatCurrencyPreferenceRepository.fiatCurrencyPreference.collectAsState()
        sweepFundsPrompt(
          id = when (props.recoveredFactor) {
            App -> DelayNotifyRecoveryEventTrackerScreenId.LOST_APP_DELAY_NOTIFY_SWEEP_SIGN_PSBTS_PROMPT
            Hardware -> HardwareRecoveryEventTrackerScreenId.LOST_HW_DELAY_NOTIFY_SWEEP_SIGN_PSBTS_PROMPT
            null -> InactiveWalletSweepEventTrackerScreenId.INACTIVE_WALLET_SWEEP_SIGN_PSBTS_PROMPT
          },
          recoveredFactor = props.recoveredFactor,
          fee = moneyAmountUiStateMachine.model(
            MoneyAmountUiProps(
              primaryMoney = sweepData.totalFeeAmount,
              secondaryAmountCurrency = fiatCurrency
            )
          ),
          transferAmount = moneyAmountUiStateMachine.model(
            MoneyAmountUiProps(
              primaryMoney = sweepData.totalTransferAmount,
              secondaryAmountCurrency = fiatCurrency
            )
          ),
          onBack = when (props.recoveredFactor) {
            null -> props.onExit
            else -> null
          },
          onHelpClick = {
            setState(ScreenState.ShowingHelpText)
          },
          onShowNetworkFeesInfo = { showingNetworkFeesInfo = true },
          onCloseNetworkFeesInfo = { showingNetworkFeesInfo = false },
          showNetworkFeesInfoSheet = showingNetworkFeesInfo,
          onSubmit = sweepData.startSweep,
          presentationStyle = props.presentationStyle
        )
      }

      is AwaitingHardwareSignedSweepsData ->
        nfcSessionUIStateMachine.model(
          NfcSessionUIStateMachineProps(
            session = { session, commands ->
              sweepData.needsHwSign
                .map { commands.signTransaction(session, it.psbt, it.sourceKeyset) }
                .toSet()
            },
            onSuccess = sweepData.addHwSignedSweeps,
            onCancel = props.onExit,
            screenPresentationStyle = props.presentationStyle,
            eventTrackerContext = NfcEventTrackerScreenIdContext.SIGN_MANY_TRANSACTIONS,
            shouldShowLongRunningOperation = true
          )
        )

      /** Server+App signing and broadcasting the transactions */
      is SigningAndBroadcastingSweepsData ->
        broadcastingScreenModel(
          id = when (props.recoveredFactor) {
            App -> DelayNotifyRecoveryEventTrackerScreenId.LOST_APP_DELAY_NOTIFY_SWEEP_BROADCASTING
            Hardware -> HardwareRecoveryEventTrackerScreenId.LOST_HW_DELAY_NOTIFY_SWEEP_BROADCASTING
            null -> InactiveWalletSweepEventTrackerScreenId.INACTIVE_WALLET_SWEEP_BROADCASTING
          },
          onBack = props.onExit,
          presentationStyle = props.presentationStyle
        )

      /** Terminal state: Broadcast completed */
      is SweepCompleteData ->
        sweepSuccessScreenModel(
          id = when (props.recoveredFactor) {
            App -> DelayNotifyRecoveryEventTrackerScreenId.LOST_APP_DELAY_NOTIFY_SWEEP_SUCCESS
            Hardware -> HardwareRecoveryEventTrackerScreenId.LOST_HW_DELAY_NOTIFY_SWEEP_SUCCESS
            null -> InactiveWalletSweepEventTrackerScreenId.INACTIVE_WALLET_SWEEP_SUCCESS
          },
          recoveredFactor = props.recoveredFactor,
          onDone = sweepData.proceed,
          presentationStyle = props.presentationStyle
        )

      /** Terminal error state: Sweep failed */
      is SweepFailedData ->
        sweepFailedScreenModel(
          id = when (props.recoveredFactor) {
            App -> DelayNotifyRecoveryEventTrackerScreenId.LOST_APP_DELAY_NOTIFY_SWEEP_FAILED
            Hardware -> HardwareRecoveryEventTrackerScreenId.LOST_HW_DELAY_NOTIFY_SWEEP_FAILED
            null -> InactiveWalletSweepEventTrackerScreenId.INACTIVE_WALLET_SWEEP_FAILED
          },
          onRetry = sweepData.retry,
          onExit = props.onExit,
          presentationStyle = props.presentationStyle,
          errorData = ErrorData(
            segment = when (props.recoveredFactor) {
              App -> RecoverySegment.DelayAndNotify.LostApp.Completion
              Hardware -> RecoverySegment.DelayAndNotify.LostApp.Completion
              null -> RecoverySegment.AdditionalSweep.Sweep
            },
            actionDescription = "Sweeping funds",
            cause = sweepData.cause
          )
        )
    }
  }

  private sealed interface ScreenState {
    /**
     * Currently displaying a screen based on the [SweepData]
     */
    data object ShowingSweepState : ScreenState

    /**
     * Displaying help text to explain why a sweep is required
     */
    data object ShowingHelpText : ScreenState

    /**
     * Displaying 'learn more' help center article
     */
    data class ShowingLearnMore(
      val urlString: String,
    ) : ScreenState
  }
}
