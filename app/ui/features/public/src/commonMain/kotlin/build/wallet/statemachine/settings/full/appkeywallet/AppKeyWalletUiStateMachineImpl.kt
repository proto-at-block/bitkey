package build.wallet.statemachine.settings.full.appkeywallet

import androidx.compose.runtime.*
import build.wallet.analytics.events.screen.id.EventTrackerScreenId
import build.wallet.analytics.events.screen.id.InactiveWalletSweepEventTrackerScreenId
import build.wallet.bitcoin.wallet.*
import build.wallet.di.ActivityScope
import build.wallet.di.BitkeyInject
import build.wallet.money.display.FiatCurrencyPreferenceRepository
import build.wallet.statemachine.core.*
import build.wallet.statemachine.core.form.FormBodyModel
import build.wallet.statemachine.core.form.FormHeaderModel
import build.wallet.statemachine.money.amount.MoneyAmountUiProps
import build.wallet.statemachine.money.amount.MoneyAmountUiStateMachine
import build.wallet.statemachine.recovery.RecoverySegment
import build.wallet.statemachine.recovery.sweep.*
import build.wallet.statemachine.settings.SettingsAppSegment
import build.wallet.ui.model.StandardClick
import build.wallet.ui.model.button.ButtonModel
import build.wallet.ui.model.toolbar.ToolbarAccessoryModel.IconAccessory.Companion.BackAccessory
import build.wallet.ui.model.toolbar.ToolbarModel
import com.github.michaelbull.result.fold

@BitkeyInject(ActivityScope::class)
class AppKeyWalletUiStateMachineImpl(
  private val appKeyWalletService: AppKeyWalletService,
  private val moneyAmountUiStateMachine: MoneyAmountUiStateMachine,
  private val fiatCurrencyPreferenceRepository: FiatCurrencyPreferenceRepository,
) : AppKeyWalletUiStateMachine {
  @Composable
  override fun model(props: AppKeyWalletUiProps): ScreenModel {
    var state: State by remember { mutableStateOf(State.Introduction) }
    var showingNetworkFeesInfo by remember { mutableStateOf(false) }
    val keyset = props.account.keybox.activeSpendingKeyset

    when (val current = state) {
      State.Scanning -> LaunchedEffect(keyset.localId) {
        state = appKeyWalletService.fullScan(keyset).fold(
          success = State::Result,
          failure = { State.Error(FailedPhase.Scan, it) }
        )
      }
      is State.Preparing -> LaunchedEffect(keyset.localId) {
        state = appKeyWalletService.prepareSweep(keyset).fold(
          success = { State.Reviewing(current.balance, it) },
          failure = { State.Error(FailedPhase.Prepare, it) }
        )
      }
      is State.Signing -> LaunchedEffect(current.preview) {
        state = appKeyWalletService.signSweep(keyset, current.preview.id).fold(
          success = { State.Broadcasting(current.preview) },
          failure = { State.Error(FailedPhase.Sign, it) }
        )
      }
      is State.Broadcasting -> LaunchedEffect(current.preview) {
        state = appKeyWalletService.broadcastSweep(keyset, current.preview.id).fold(
          success = { State.Complete },
          failure = { State.Error(FailedPhase.Broadcast, it) }
        )
      }
      else -> Unit
    }

    return when (val current = state) {
      State.Introduction -> body(
        "App Key Wallet",
        "Check for bitcoin associated with your app key that may not appear in your Bitkey balance. Checking shares this wallet's addresses with your configured Bitcoin server.",
        "Check wallet",
        { state = State.Scanning },
        props.onBack
      )
      State.Scanning -> loading("Checking the Bitcoin network…", props.onBack)
      is State.Result -> resultScreen(current.balance, props) { state = State.Preparing(current.balance) }
      is State.Preparing -> LoadingBodyModel(
        id = InactiveWalletSweepEventTrackerScreenId.APP_KEY_WALLET_SWEEP_GENERATING_PSBT,
        title = "Preparing your transfer…",
        onBack = props.onBack
      ).asModalScreen()
      is State.Reviewing -> sweepFundsPrompt(
        id = InactiveWalletSweepEventTrackerScreenId.APP_KEY_WALLET_SWEEP_SIGN_PSBT_PROMPT,
        sweepContext = SweepFundsPromptContext.AppKeyWallet,
        transferAmount = moneyAmountUiStateMachine.model(
          MoneyAmountUiProps(current.preview.sourceBalance, fiatCurrencyPreferenceRepository.fiatCurrencyPreference.value)
        ),
        fee = moneyAmountUiStateMachine.model(
          MoneyAmountUiProps(current.preview.fee, fiatCurrencyPreferenceRepository.fiatCurrencyPreference.value)
        ),
        onShowNetworkFeesInfo = { showingNetworkFeesInfo = true },
        onCloseNetworkFeesInfo = { showingNetworkFeesInfo = false },
        showNetworkFeesInfoSheet = showingNetworkFeesInfo,
        onBack = { state = State.Result(current.balance) },
        onHelpClick = { state = State.ShowingHelp(current) },
        onSubmit = { state = State.Signing(current.balance, current.preview) },
        presentationStyle = ScreenPresentationStyle.Modal
      )
      is State.ShowingHelp -> sweepInactiveHelpModel(
        id = InactiveWalletSweepEventTrackerScreenId.INACTIVE_WALLET_HELP,
        presentationStyle = ScreenPresentationStyle.Modal,
        onLearnMore = { state = current.reviewing },
        onBack = { state = current.reviewing }
      )
      is State.Signing -> loading("Securing your App Key Wallet transfer…", null)
      is State.Broadcasting -> broadcastingScreenModel(
        id = InactiveWalletSweepEventTrackerScreenId.APP_KEY_WALLET_SWEEP_BROADCASTING,
        context = build.wallet.recovery.sweep.SweepContext.InactiveWallet,
        onBack = {},
        presentationStyle = ScreenPresentationStyle.Modal,
        title = "Transferring funds to your Bitkey wallet…"
      )
      State.Complete -> sweepSuccessScreenModel(
        id = InactiveWalletSweepEventTrackerScreenId.APP_KEY_WALLET_SWEEP_SUCCESS,
        recoveredFactor = null,
        onDone = props.onBack,
        presentationStyle = ScreenPresentationStyle.Modal,
        title = "Funds moved to your Bitkey wallet",
        subline = "Your funds are now included in your main Bitkey balance."
      )
      is State.Error -> errorScreen(current, props) { state = it }
    }
  }

  private fun resultScreen(
    balance: AppKeyWalletBalance,
    props: AppKeyWalletUiProps,
    onReview: () -> Unit,
  ) = when {
    balance.totalSats == 0uL -> body(
      "No funds found", "No funds were found in your App Key Wallet.", "Done", props.onBack, props.onBack
    )
    balance.spendableSats == 0uL -> body(
      "Funds found", "Found ${balance.totalSats} sats. These funds are waiting for confirmation.", "Done", props.onBack, props.onBack
    )
    else -> body(
      "Funds found", "Found ${balance.spendableSats} spendable sats in your App Key Wallet.", "Review transfer", onReview, props.onBack
    )
  }

  private fun errorScreen(
    error: State.Error,
    props: AppKeyWalletUiProps,
    setState: (State) -> Unit,
  ): ScreenModel {
    if (error.error is AppKeyWalletServiceError.PrivateKeyUnavailable) {
      return ErrorFormBodyModel(
        title = "App key unavailable",
        subline = error.error.message,
        primaryButton = ButtonDataModel(text = "Done", onClick = props.onBack),
        onBack = props.onBack,
        eventTrackerScreenId = AppKeyWalletScreenId,
        errorData = ErrorData(SettingsAppSegment.AppKeyWallet, "Scanning app-key wallet", error.error)
      ).asModalScreen()
    }
    if (error.phase == FailedPhase.Broadcast) {
      return sweepFailedScreenModel(
        id = InactiveWalletSweepEventTrackerScreenId.APP_KEY_WALLET_SWEEP_FAILED,
        presentationStyle = ScreenPresentationStyle.Modal,
        errorData = ErrorData(RecoverySegment.AdditionalSweep.Sweep, "Broadcasting App Key Wallet transfer", error.error),
        onRetry = { setState(State.Scanning) },
        onExit = props.onBack,
        title = "We couldn't transfer your App Key Wallet funds"
      )
    }
    return ErrorFormBodyModel(
      title = when (error.phase) {
        FailedPhase.Scan -> "We couldn't check your wallet"
        FailedPhase.Prepare -> "We couldn't prepare your transfer"
        FailedPhase.Sign -> "We couldn't sign your transfer"
        FailedPhase.Broadcast -> error("handled above")
      },
      subline = error.error.message,
      primaryButton = ButtonDataModel(
        text = "Try again",
        onClick = { setState(State.Scanning) }
      ),
      onBack = props.onBack,
      eventTrackerScreenId = when (error.phase) {
        FailedPhase.Prepare -> InactiveWalletSweepEventTrackerScreenId.APP_KEY_WALLET_SWEEP_GENERATING_PSBT
        else -> AppKeyWalletScreenId
      },
      errorData = ErrorData(SettingsAppSegment.AppKeyWallet, "${error.phase.action} App Key Wallet funds", error.error)
    ).asModalScreen()
  }

  private fun loading(title: String, onBack: (() -> Unit)?) = LoadingBodyModel(
    id = AppKeyWalletScreenId,
    title = title,
    onBack = onBack
  ).asModalScreen()

  private fun body(
    title: String,
    description: String,
    primaryText: String,
    onPrimary: () -> Unit,
    onBack: () -> Unit,
  ) = AppKeyWalletBodyModel(title, description, primaryText, onPrimary, onBack).asModalScreen()

  private enum class FailedPhase(val action: String) {
    Scan("Scanning"), Prepare("Preparing"), Sign("Signing"), Broadcast("Broadcasting")
  }

  private sealed interface State {
    data object Introduction : State
    data object Scanning : State
    data class Result(val balance: AppKeyWalletBalance) : State
    data class Preparing(val balance: AppKeyWalletBalance) : State
    data class Reviewing(val balance: AppKeyWalletBalance, val preview: AppKeyWalletSweepPreview) : State
    data class ShowingHelp(val reviewing: Reviewing) : State
    data class Signing(val balance: AppKeyWalletBalance, val preview: AppKeyWalletSweepPreview) : State
    data class Broadcasting(val preview: AppKeyWalletSweepPreview) : State
    data object Complete : State
    data class Error(val phase: FailedPhase, val error: AppKeyWalletServiceError) : State
  }
}

private object AppKeyWalletScreenId : EventTrackerScreenId {
  override val name = "APP_KEY_WALLET"
}

private data class AppKeyWalletBodyModel(
  val title: String,
  val description: String,
  val primaryText: String,
  val onPrimary: () -> Unit,
  override val onBack: () -> Unit,
) : FormBodyModel(
    id = AppKeyWalletScreenId,
    onBack = onBack,
    toolbar = ToolbarModel(leadingAccessory = BackAccessory(onClick = onBack)),
    header = FormHeaderModel(headline = title, subline = description),
    primaryButton = ButtonModel(
      text = primaryText,
      size = ButtonModel.Size.Footer,
      onClick = StandardClick(onPrimary)
    )
  )
