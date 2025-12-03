package build.wallet.statemachine.send.hardwareconfirmation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import build.wallet.di.ActivityScope
import build.wallet.di.BitkeyInject
import build.wallet.platform.web.InAppBrowserNavigator
import build.wallet.statemachine.core.InAppBrowserModel
import build.wallet.statemachine.core.ScreenModel

@BitkeyInject(ActivityScope::class)
class HardwareConfirmationUiStateMachineImpl(
  private val inAppBrowserNavigator: InAppBrowserNavigator,
) : HardwareConfirmationUiStateMachine {
  @Composable
  override fun model(props: HardwareConfirmationUiProps): ScreenModel {
    var uiState: HardwareConfirmationUiState by remember {
      mutableStateOf(HardwareConfirmationUiState.ShowingConfirmation)
    }

    return when (uiState) {
      HardwareConfirmationUiState.ShowingConfirmation -> {
        ScreenModel(
          body = HardwareConfirmationScreenModel(
            onBack = {
              uiState = HardwareConfirmationUiState.ShowingCancellation
            },
            onSend = { props.onConfirm() },
            onLearnMore = {
              uiState = HardwareConfirmationUiState.ShowingLearnMore
            }
          )
        )
      }

      HardwareConfirmationUiState.ShowingLearnMore -> {
        InAppBrowserModel(
          open = {
            inAppBrowserNavigator.open(
              url = HardwareConfirmationUiStateMachine.HARDWARE_CONFIRMATION_LEARN_MORE_URL,
              onClose = {
                uiState = HardwareConfirmationUiState.ShowingConfirmation
              }
            )
          }
        ).asModalScreen()
      }
      HardwareConfirmationUiState.ShowingCancellation -> {
        ScreenModel(
          body = HardwareConfirmationCanceledScreenModel(
            onBack = props.onBack
          )
        )
      }
    }
  }
}

private sealed interface HardwareConfirmationUiState {
  data object ShowingConfirmation : HardwareConfirmationUiState

  data object ShowingLearnMore : HardwareConfirmationUiState

  data object ShowingCancellation : HardwareConfirmationUiState
}
