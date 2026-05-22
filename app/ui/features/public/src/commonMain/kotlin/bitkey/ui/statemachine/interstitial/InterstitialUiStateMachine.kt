package bitkey.ui.statemachine.interstitial

import build.wallet.bitkey.account.FullAccount
import build.wallet.statemachine.core.ScreenModel
import build.wallet.statemachine.core.SheetModel
import build.wallet.statemachine.core.StateMachine

/**
 * State machine for managing the various potential interstitial screens shown upon foregrounding the app.
 * When the result is null, it indicates that no interstitial screen should be shown.
 */
interface InterstitialUiStateMachine : StateMachine<InterstitialUiProps, InterstitialUiModel?>

sealed interface InterstitialUiModel {
  data class Screen(
    val screenModel: ScreenModel,
  ) : InterstitialUiModel

  data class Sheet(
    val sheetModel: SheetModel,
  ) : InterstitialUiModel
}

/**
 * @property account The full account for which the interstitial screen is being displayed.
 * @property isComingFromOnboarding Indicates whether the user is coming from the onboarding flow.
 */
data class InterstitialUiProps(
  val account: FullAccount,
  val isComingFromOnboarding: Boolean,
)
