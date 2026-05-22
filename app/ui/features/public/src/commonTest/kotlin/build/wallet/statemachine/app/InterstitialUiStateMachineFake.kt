package build.wallet.statemachine.app

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import bitkey.ui.statemachine.interstitial.InterstitialUiModel
import bitkey.ui.statemachine.interstitial.InterstitialUiProps
import bitkey.ui.statemachine.interstitial.InterstitialUiStateMachine
import build.wallet.statemachine.BodyModelMock

class InterstitialUiStateMachineFake : InterstitialUiStateMachine {
  var shouldShowInterstitial by mutableStateOf(false)
  var shouldShowSheetInterstitial by mutableStateOf(false)

  @Composable
  override fun model(props: InterstitialUiProps): InterstitialUiModel? {
    return when {
      props.isComingFromOnboarding -> null
      shouldShowSheetInterstitial -> InterstitialUiModel.Sheet(
        BodyModelMock(
          id = BODY_MODEL_ID,
          latestProps = props
        ).asSheetModalScreen(onClosed = {})
      )
      shouldShowInterstitial -> InterstitialUiModel.Screen(
        BodyModelMock(
          id = BODY_MODEL_ID,
          latestProps = props
        ).asRootScreen()
      )
      else -> null
    }
  }

  fun reset() {
    shouldShowInterstitial = false
    shouldShowSheetInterstitial = false
  }

  companion object {
    const val BODY_MODEL_ID = "interstitial-ui-state-machine"
  }
}
