package bitkey.ui.statemachine.interstitial

import androidx.compose.runtime.*
import bitkey.account.isW3Hardware
import build.wallet.coachmark.CoachmarkIdentifier
import build.wallet.coachmark.CoachmarkService
import build.wallet.compose.coroutines.rememberStableCoroutineScope
import build.wallet.device.wipe.DeviceWipeEligibilityService
import build.wallet.device.wipe.InactiveHardwareDevice
import build.wallet.device.wipe.OldW1WipeReadiness
import build.wallet.di.ActivityScope
import build.wallet.di.BitkeyInject
import build.wallet.inheritance.InheritanceUpsellService
import build.wallet.navigation.v1.NavigationScreenId
import build.wallet.platform.web.InAppBrowserNavigator
import build.wallet.router.Route
import build.wallet.router.Router
import build.wallet.statemachine.core.InAppBrowserModel
import build.wallet.statemachine.inheritance.InheritanceUpsellBodyModel
import build.wallet.statemachine.settings.full.device.wipedevice.WipeContext
import build.wallet.statemachine.settings.full.device.wipedevice.WipingDeviceInitialStep
import build.wallet.statemachine.settings.full.device.wipedevice.WipingDeviceProps
import build.wallet.statemachine.settings.full.device.wipedevice.WipingDeviceUiStateMachine
import build.wallet.statemachine.walletmigration.W3UpgradeBlockerBodyModel
import com.github.michaelbull.result.fold
import com.github.michaelbull.result.getOr
import kotlinx.coroutines.launch

@BitkeyInject(ActivityScope::class)
class InterstitialUiStateMachineImpl(
  private val inheritanceUpsellService: InheritanceUpsellService,
  private val coachmarkService: CoachmarkService,
  private val inAppBrowserNavigator: InAppBrowserNavigator,
  private val deviceWipeEligibilityService: DeviceWipeEligibilityService,
  private val wipingDeviceUiStateMachine: WipingDeviceUiStateMachine,
) : InterstitialUiStateMachine {
  @Composable
  override fun model(props: InterstitialUiProps): InterstitialUiModel? {
    val scope = rememberStableCoroutineScope()

    // Resolve W3 blocker eligibility first so we can decide precedence before deciding to show
    // (and mark as seen) the inheritance upsell. Use nullable to distinguish "not yet resolved"
    // from "resolved to false".
    val shouldShowW3UpgradeBlocker by produceState<Boolean?>(null, props.account) {
      // W3 hardware accounts have already upgraded, don't show the blocker
      if (props.account.config.isW3Hardware) {
        value = false
        return@produceState
      }
      val coachmarks = coachmarkService.coachmarksToDisplay(
        setOf(CoachmarkIdentifier.W3UpgradeBlockerCoachmark)
      ).getOr(emptyList())
      value = CoachmarkIdentifier.W3UpgradeBlockerCoachmark in coachmarks
    }

    val oldW1ReminderEligibility by produceState<OldW1ReminderEligibility>(
      initialValue = OldW1ReminderEligibility.Loading,
      props.account,
      props.isComingFromOnboarding,
      shouldShowW3UpgradeBlocker
    ) {
      if (props.isComingFromOnboarding || shouldShowW3UpgradeBlocker != false) {
        value = OldW1ReminderEligibility.NotReady
        return@produceState
      }

      value = OldW1ReminderEligibility.Loading
      value = deviceWipeEligibilityService.oldW1WipeReadiness(props.account)
        .fold(
          success = { readiness ->
            when (readiness) {
              is OldW1WipeReadiness.Ready -> OldW1ReminderEligibility.Ready(readiness.device)
              OldW1WipeReadiness.NotReady -> OldW1ReminderEligibility.NotReady
            }
          },
          failure = { OldW1ReminderEligibility.NotReady }
        )
    }

    var hasShownInterstitialThisSession by remember { mutableStateOf(false) }

    // Only check inheritance upsell eligibility once the W3 blocker check has resolved and is
    // not eligible and the old-W1 reminder check has resolved. This prevents marking the upsell
    // as seen while a higher-priority interstitial is still about to be shown.
    val shouldShowInheritanceUpsell by produceState(
      false,
      shouldShowW3UpgradeBlocker,
      oldW1ReminderEligibility
    ) {
      if (shouldShowW3UpgradeBlocker != false ||
        oldW1ReminderEligibility !is OldW1ReminderEligibility.NotReady
      ) {
        value = false
        return@produceState
      }

      value = inheritanceUpsellService.shouldShowUpsell()
    }

    val readyOldW1Device =
      (oldW1ReminderEligibility as? OldW1ReminderEligibility.Ready)?.device

    // We attempt to show the interstitial screen only when the flags change.
    //
    // We gate directly on `props.isComingFromOnboarding` (rather than latching it locally) so the
    // "no interstitial right after onboarding" guarantee survives async eligibility resolution:
    // a latched flag would be flipped to false on the first composition (before eligibility has
    // resolved) and then a later resolution to true would surface the interstitial anyway.
    var uiState: State by remember(
      shouldShowInheritanceUpsell,
      shouldShowW3UpgradeBlocker,
      oldW1ReminderEligibility,
      props.isComingFromOnboarding
    ) {
      // We only show one interstitial screen at a time:
      // 1. If the app is coming from onboarding, never show an interstitial (regardless of when
      //    eligibility resolves).
      // 2. If the user should see the W3 upgrade blocker, we show that screen.
      // 3. If the old W1 is ready to wipe, we show the old-device wipe reminder.
      // 4. If the user should see the inheritance upsell, we show the InheritanceUpsell screen
      // 5. If any interstitial has already been shown in this session, or none of the above
      //    conditions are met, we show no interstitial screen.
      mutableStateOf(
        nextInterstitialState(
          isComingFromOnboarding = props.isComingFromOnboarding,
          hasShownInterstitialThisSession = hasShownInterstitialThisSession,
          shouldShowW3UpgradeBlocker = shouldShowW3UpgradeBlocker,
          readyOldW1Device = readyOldW1Device,
          shouldShowInheritanceUpsell = shouldShowInheritanceUpsell,
          onInterstitialShown = {
            hasShownInterstitialThisSession = true
          }
        )
      )
    }

    return when (val state = uiState) {
      State.W3UpgradeBlocker -> {
        InterstitialUiModel.Screen(
          W3UpgradeBlockerBodyModel(
            onGetStarted = {
              scope.launch {
                coachmarkService.markCoachmarkAsDisplayed(CoachmarkIdentifier.W3UpgradeBlockerCoachmark)
              }
              uiState = State.W3UpgradeBlockerBrowser
            },
            onClose = {
              scope.launch {
                coachmarkService.markCoachmarkAsDisplayed(CoachmarkIdentifier.W3UpgradeBlockerCoachmark)
              }
              uiState = State.None
            }
          ).asModalFullScreen()
        )
      }
      State.W3UpgradeBlockerBrowser -> {
        InterstitialUiModel.Screen(
          InAppBrowserModel(
            open = {
              inAppBrowserNavigator.open(
                url = "https://bitkey.world/",
                onClose = {
                  uiState = State.None
                }
              )
            }
          ).asModalFullScreen()
        )
      }
      State.InheritanceUpsell -> {
        scope.launch {
          inheritanceUpsellService.markUpsellAsSeen()
        }

        InterstitialUiModel.Screen(
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
        )
      }
      is State.W3UpgradeOldDeviceWipeReady -> {
        fun markWipeReminderDismissed() {
          scope.launch {
            deviceWipeEligibilityService.markOldW1WipeReminderDismissed()
          }
        }

        fun dismissWipeReminder() {
          markWipeReminderDismissed()
          uiState = State.None
        }

        InterstitialUiModel.Sheet(
          W3UpgradeOldDeviceWipeReadyBodyModel(
            onWipeOldDevice = {
              markWipeReminderDismissed()
              uiState = State.WipingW3UpgradeOldDevice(state.device)
            },
            onDone = ::dismissWipeReminder
          ).asSheetModalScreen(onClosed = ::dismissWipeReminder)
        )
      }
      is State.WipingW3UpgradeOldDevice ->
        InterstitialUiModel.Screen(
          wipingDeviceUiStateMachine.model(
            WipingDeviceProps(
              onBack = { uiState = State.None },
              onSuccess = { uiState = State.None },
              fullAccount = props.account,
              initialStep = WipingDeviceInitialStep.ScanDevice,
              wipeContext = WipeContext.InactiveDevice(state.device)
            )
          )
        )
      State.None -> null
    }
  }
}

private fun nextInterstitialState(
  isComingFromOnboarding: Boolean,
  hasShownInterstitialThisSession: Boolean,
  shouldShowW3UpgradeBlocker: Boolean?,
  readyOldW1Device: InactiveHardwareDevice?,
  shouldShowInheritanceUpsell: Boolean,
  onInterstitialShown: () -> Unit,
): State {
  if (isComingFromOnboarding || hasShownInterstitialThisSession) {
    return State.None
  }

  return when {
    shouldShowW3UpgradeBlocker == true -> State.W3UpgradeBlocker
    readyOldW1Device != null -> State.W3UpgradeOldDeviceWipeReady(readyOldW1Device)
    shouldShowInheritanceUpsell -> State.InheritanceUpsell
    else -> State.None
  }.also { state ->
    if (state != State.None) {
      onInterstitialShown()
    }
  }
}

private sealed interface State {
  data object W3UpgradeBlocker : State

  data object W3UpgradeBlockerBrowser : State

  data object InheritanceUpsell : State

  data class W3UpgradeOldDeviceWipeReady(
    val device: InactiveHardwareDevice,
  ) : State

  data class WipingW3UpgradeOldDevice(
    val device: InactiveHardwareDevice,
  ) : State

  data object None : State
}

private sealed interface OldW1ReminderEligibility {
  data object Loading : OldW1ReminderEligibility

  data object NotReady : OldW1ReminderEligibility

  data class Ready(
    val device: InactiveHardwareDevice,
  ) : OldW1ReminderEligibility
}
