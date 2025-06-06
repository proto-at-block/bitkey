package bitkey.ui.statemachine.interstitial

import androidx.compose.runtime.*
import bitkey.recovery.RecoveryStatusService
import bitkey.recovery.fundslost.AtRiskCause
import bitkey.recovery.fundslost.AtRiskCause.MissingHardware
import bitkey.recovery.fundslost.FundsLostRiskLevel
import bitkey.recovery.fundslost.FundsLostRiskService
import build.wallet.compose.coroutines.rememberStableCoroutineScope
import build.wallet.di.ActivityScope
import build.wallet.di.BitkeyInject
import build.wallet.inheritance.InheritanceUpsellService
import build.wallet.navigation.v1.NavigationScreenId
import build.wallet.platform.app.AppSessionManager
import build.wallet.platform.app.AppSessionState
import build.wallet.recovery.Recovery
import build.wallet.recovery.socrec.PostSocRecTaskRepository
import build.wallet.router.Route
import build.wallet.router.Router
import build.wallet.statemachine.core.ScreenModel
import build.wallet.statemachine.core.ScreenPresentationStyle
import build.wallet.statemachine.data.recovery.losthardware.LostHardwareRecoveryDataStateMachine
import build.wallet.statemachine.data.recovery.losthardware.LostHardwareRecoveryProps
import build.wallet.statemachine.inheritance.InheritanceUpsellBodyModel
import build.wallet.statemachine.recovery.losthardware.LostHardwareRecoveryUiStateMachine
import build.wallet.statemachine.recovery.losthardware.initiate.InstructionsStyle
import com.github.michaelbull.result.get
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

@BitkeyInject(ActivityScope::class)
class InterstitialUiStateMachineImpl(
  private val lostHardwareRecoveryDataStateMachine: LostHardwareRecoveryDataStateMachine,
  private val lostHardwareUiStateMachine: LostHardwareRecoveryUiStateMachine,
  private val recoveryStatusService: RecoveryStatusService,
  private val fundsLostRiskService: FundsLostRiskService,
  private val inheritanceUpsellService: InheritanceUpsellService,
  private val appSessionManager: AppSessionManager,
  private val recoveryIncompleteRepository: PostSocRecTaskRepository,
) : InterstitialUiStateMachine {
  @Composable
  override fun model(props: InterstitialUiProps): ScreenModel? {
    val scope = rememberStableCoroutineScope()

    val recovery by remember {
      recoveryStatusService.status()
        .map { it.get() }
        .filterIsInstance<Recovery.StillRecovering?>()
    }.collectAsState(null)

    val appSessionState by remember {
      appSessionManager.appSessionState
    }.collectAsState()

    val lostHardwareRecoveryData = lostHardwareRecoveryDataStateMachine.model(
      props = LostHardwareRecoveryProps(
        account = props.account,
        hardwareRecovery = recovery
      )
    )

    val fundsLostRisk by remember {
      fundsLostRiskService.riskLevel()
    }.collectAsState()

    val shouldShowInheritanceUpsell by produceState<Boolean>(false) {
      value = inheritanceUpsellService.shouldShowUpsell()
    }

    var isComingFromOnboarding by remember { mutableStateOf(props.isComingFromOnboarding) }

    // We attempt to show the interstitial screen only when the session state changes
    // We also re-evaluate the UI state when inheritance upsell flag changes
    var uiState: State by remember(appSessionState, shouldShowInheritanceUpsell) {
      // Prior to determining the UI state, we check if the app is coming from onboarding to avoid
      // showing the interstitial screen immediately after onboarding.
      // We also check the app session state to ensure that we only update the interstitial screen
      // when the app is foregrounded.

      // We only show one interstitial screen at a time, so we determine the state based on the conditions
      // 1. If the funds are at risk, we show the FundsLostRisk screen
      // 2. If the user should see the inheritance upsell, we show the InheritanceUpsell screen
      // 3. If none of the above conditions are met, we show no interstitial screen
      when {
        isComingFromOnboarding -> {
          // If the app is coming from onboarding, set isComingFromOnboarding to false so on next recomposition,
          // we don't opt out of showing the interstitial screen again
          isComingFromOnboarding = false
          mutableStateOf(State.None)
        }
        appSessionState != AppSessionState.FOREGROUND -> mutableStateOf(State.None)
        fundsLostRisk != FundsLostRiskLevel.Protected -> mutableStateOf(State.FundsLostRisk)
        shouldShowInheritanceUpsell -> mutableStateOf(State.InheritanceUpsell)
        else -> mutableStateOf(State.None)
      }
    }

    return when (uiState) {
      State.FundsLostRisk -> {
        when (val risk = fundsLostRisk) {
          FundsLostRiskLevel.Protected -> null // Should not happen, but just in case
          is FundsLostRiskLevel.AtRisk -> when (risk.cause) {
            is AtRiskCause.MissingCloudBackup -> {
              Router.route = Route.NavigationDeeplink(screen = NavigationScreenId.NAVIGATION_SCREEN_ID_CLOUD_REPAIR)
              uiState = State.None
              null
            }
            AtRiskCause.MissingContactMethod, MissingHardware -> WalletAtRiskInterstitialBodyModel(
              subline = fundsLostRisk.sublineText(),
              buttonText = fundsLostRisk.buttonText(),
              onButtonClick = {
                val screenId = fundsLostRisk.screenId()
                Router.route = Route.NavigationDeeplink(screen = screenId)
                uiState = State.None
              },
              onClose = {
                uiState = State.None
              }
            ).asModalFullScreen()
          }
        }
      }
      State.InheritanceUpsell -> {
        scope.launch {
          inheritanceUpsellService.markUpsellAsSeen()
        }

        InheritanceUpsellBodyModel(
          onGetStarted = {
            Router.route =
              Route.NavigationDeeplink(screen = NavigationScreenId.NAVIGATION_SCREEN_ID_MANAGE_INHERITANCE)
            uiState = State.None
          },
          onClose = {
            uiState = State.None
          }
        ).asModalFullScreen()
      }
      State.CompleteRecovery -> lostHardwareUiStateMachine.model(
        props = build.wallet.statemachine.recovery.losthardware.LostHardwareRecoveryProps(
          account = props.account,
          lostHardwareRecoveryData = lostHardwareRecoveryData,
          onExit = { uiState = State.None },
          onFoundHardware = {
            scope.launch {
              // Set the flag to no longer show the replace hardware card nudge
              // this flag is used by the MoneyHomeCardsUiStateMachine
              // and toggled on by the FullAccountCloudBackupRestorationUiStateMachine
              recoveryIncompleteRepository.setHardwareReplacementNeeded(false)
            }
            uiState = State.None
          },
          screenPresentationStyle = ScreenPresentationStyle.Modal,
          instructionsStyle = InstructionsStyle.Independent,
          onComplete = { uiState = State.None }
        )
      )
      State.None -> null
    }
  }
}

private fun FundsLostRiskLevel.sublineText(): String {
  return when (this) {
    FundsLostRiskLevel.Protected -> error("Protected risk level should not be shown in interstitial")
    is FundsLostRiskLevel.AtRisk -> when (this.cause) {
      is AtRiskCause.MissingCloudBackup -> "Your wallet is not backed up to the cloud. If you lose your phone or delete the app, you won’t be able to recover your wallet—or your bitcoin.\n\nBack up your wallet to the cloud now to protect and access the bitcoin on this wallet."
      AtRiskCause.MissingContactMethod -> "Your wallet is not set up to receive critical alerts. Critical alerts help recover your wallet.\n\nAdd a contact method now to protect and access the bitcoin on this wallet."
      AtRiskCause.MissingHardware -> "Your wallet is not paired to a Bitkey device. If you lose your phone or delete the app, you won’t be able to recover your wallet—or your bitcoin.\n\nPair a Bitkey device now to protect and access the bitcoin on this wallet. "
    }
  }
}

private fun FundsLostRiskLevel.buttonText(): String {
  return when (this) {
    FundsLostRiskLevel.Protected -> error("Protected risk level should not be shown in interstitial")
    is FundsLostRiskLevel.AtRisk -> when (this.cause) {
      is AtRiskCause.MissingCloudBackup -> "Back up to the cloud"
      AtRiskCause.MissingContactMethod -> "Add a contact method"
      AtRiskCause.MissingHardware -> "Add a Bitkey device"
    }
  }
}

private fun FundsLostRiskLevel.screenId(): NavigationScreenId {
  return when (this) {
    FundsLostRiskLevel.Protected -> error("Protected risk level should not be shown in interstitial")
    is FundsLostRiskLevel.AtRisk -> when (this.cause) {
      is AtRiskCause.MissingCloudBackup -> NavigationScreenId.NAVIGATION_SCREEN_ID_MOBILE_KEY_BACKUP
      AtRiskCause.MissingContactMethod -> NavigationScreenId.NAVIGATION_SCREEN_ID_MANAGE_CRITICAL_ALERTS
      AtRiskCause.MissingHardware -> NavigationScreenId.NAVIGATION_SCREEN_ID_PAIR_DEVICE
    }
  }
}

private sealed interface State {
  data object FundsLostRisk : State

  data object InheritanceUpsell : State

  data object CompleteRecovery : State

  data object None : State
}
