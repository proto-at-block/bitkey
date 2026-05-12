package bitkey.ui.statemachine.interstitial

import androidx.compose.runtime.*
import bitkey.account.isW3Hardware
import build.wallet.coachmark.CoachmarkIdentifier
import build.wallet.coachmark.CoachmarkService
import build.wallet.compose.coroutines.rememberStableCoroutineScope
import build.wallet.di.ActivityScope
import build.wallet.di.BitkeyInject
import build.wallet.inheritance.InheritanceUpsellService
import build.wallet.navigation.v1.NavigationScreenId
import build.wallet.platform.web.InAppBrowserNavigator
import build.wallet.router.Route
import build.wallet.router.Router
import build.wallet.statemachine.core.InAppBrowserModel
import build.wallet.statemachine.core.ScreenModel
import build.wallet.statemachine.inheritance.InheritanceUpsellBodyModel
import build.wallet.statemachine.walletmigration.W3UpgradeBlockerBodyModel
import com.github.michaelbull.result.getOr
import kotlinx.coroutines.launch

@BitkeyInject(ActivityScope::class)
class InterstitialUiStateMachineImpl(
  private val inheritanceUpsellService: InheritanceUpsellService,
  private val coachmarkService: CoachmarkService,
  private val inAppBrowserNavigator: InAppBrowserNavigator,
) : InterstitialUiStateMachine {
  @Composable
  override fun model(props: InterstitialUiProps): ScreenModel? {
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

    // Only check inheritance upsell eligibility once the W3 blocker check has resolved and is
    // not eligible. This prevents marking the upsell as seen while the W3 blocker is still
    // about to be shown.
    val shouldShowInheritanceUpsell by produceState(false, shouldShowW3UpgradeBlocker) {
      if (shouldShowW3UpgradeBlocker == false) {
        value = inheritanceUpsellService.shouldShowUpsell()
      }
    }

    // Tracks whether the W3 upgrade blocker has already been shown in this session, so the
    // inheritance upsell does not appear immediately after the user dismisses the blocker
    // (e.g., once the coachmark is marked as displayed, blocker eligibility flips to false and
    // would otherwise allow the inheritance upsell to take over on the next recomposition).
    var hasShownW3UpgradeBlocker by remember { mutableStateOf(false) }

    // We attempt to show the interstitial screen only when the flags change.
    //
    // We gate directly on `props.isComingFromOnboarding` (rather than latching it locally) so the
    // "no interstitial right after onboarding" guarantee survives async eligibility resolution:
    // a latched flag would be flipped to false on the first composition (before eligibility has
    // resolved) and then a later resolution to true would surface the interstitial anyway.
    var uiState: State by remember(
      shouldShowInheritanceUpsell,
      shouldShowW3UpgradeBlocker,
      props.isComingFromOnboarding
    ) {
      // We only show one interstitial screen at a time, so we determine the state based on the conditions:
      // 1. If the app is coming from onboarding, never show an interstitial (regardless of when
      //    eligibility resolves).
      // 2. If the user should see the W3 upgrade blocker, we show that screen.
      // 3. If the user should see the inheritance upsell, we show the InheritanceUpsell screen
      //    (suppressed if the W3 upgrade blocker was already shown this session).
      // 4. If none of the above conditions are met, we show no interstitial screen.
      when {
        props.isComingFromOnboarding -> mutableStateOf(State.None)
        shouldShowW3UpgradeBlocker == true -> {
          hasShownW3UpgradeBlocker = true
          mutableStateOf(State.W3UpgradeBlocker)
        }
        shouldShowInheritanceUpsell && !hasShownW3UpgradeBlocker ->
          mutableStateOf(State.InheritanceUpsell)
        else -> mutableStateOf(State.None)
      }
    }

    return when (uiState) {
      State.W3UpgradeBlocker -> {
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
      }
      State.W3UpgradeBlockerBrowser -> {
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
      State.None -> null
    }
  }
}

private sealed interface State {
  data object W3UpgradeBlocker : State

  data object W3UpgradeBlockerBrowser : State

  data object InheritanceUpsell : State

  data object None : State
}
