package bitkey.ui.statemachine.interstitial

import androidx.compose.runtime.*
import build.wallet.compose.coroutines.rememberStableCoroutineScope
import build.wallet.di.ActivityScope
import build.wallet.di.BitkeyInject
import build.wallet.inheritance.InheritanceUpsellService
import build.wallet.navigation.v1.NavigationScreenId
import build.wallet.router.Route
import build.wallet.router.Router
import build.wallet.statemachine.core.ScreenModel
import build.wallet.statemachine.inheritance.InheritanceUpsellBodyModel
import kotlinx.coroutines.launch

@BitkeyInject(ActivityScope::class)
class InterstitialUiStateMachineImpl(
  private val inheritanceUpsellService: InheritanceUpsellService,
) : InterstitialUiStateMachine {
  @Composable
  override fun model(props: InterstitialUiProps): ScreenModel? {
    val scope = rememberStableCoroutineScope()

    val shouldShowInheritanceUpsell by produceState(false) {
      value = inheritanceUpsellService.shouldShowUpsell()
    }

    var isComingFromOnboarding by remember { mutableStateOf(props.isComingFromOnboarding) }

    // We attempt to show the interstitial screen only when the inheritance upsell flag changes
    var uiState: State by remember(shouldShowInheritanceUpsell) {
      // Prior to determining the UI state, we check if the app is coming from onboarding to avoid
      // showing the interstitial screen immediately after onboarding.

      // We only show one interstitial screen at a time, so we determine the state based on the conditions:
      // 1. If the user should see the inheritance upsell, we show the InheritanceUpsell screen
      // 2. If none of the above conditions are met, we show no interstitial screen
      when {
        isComingFromOnboarding -> {
          // If the app is coming from onboarding, set isComingFromOnboarding to false so on next recomposition,
          // we don't opt out of showing the interstitial screen again
          isComingFromOnboarding = false
          mutableStateOf(State.None)
        }
        shouldShowInheritanceUpsell -> mutableStateOf(State.InheritanceUpsell)
        else -> mutableStateOf(State.None)
      }
    }

    return when (uiState) {
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
  data object InheritanceUpsell : State

  data object None : State
}
