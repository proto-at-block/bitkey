package build.wallet.statemachine.app

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import bitkey.ui.statemachine.interstitial.InterstitialUiProps
import bitkey.ui.statemachine.interstitial.InterstitialUiStateMachine
import build.wallet.statemachine.BodyModelMock
import build.wallet.statemachine.core.ScreenModel

class InterstitialUiStateMachineFake : InterstitialUiStateMachine {
  var shouldShowInterstitial by mutableStateOf(false)

  @Composable
  override fun model(props: InterstitialUiProps): ScreenModel? {
    return when {
      props.isComingFromOnboarding -> null
      shouldShowInterstitial.not() -> null
      else -> BodyModelMock(
        id = BODY_MODEL_ID,
        latestProps = props
      ).asRootScreen()
    }
  }

  fun reset() {
    shouldShowInterstitial = false
  }

  companion object {
    const val BODY_MODEL_ID = "interstitial-ui-state-machine"
  }
}
