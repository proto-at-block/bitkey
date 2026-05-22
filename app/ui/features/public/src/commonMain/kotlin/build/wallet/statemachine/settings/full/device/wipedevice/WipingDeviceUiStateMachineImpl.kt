package build.wallet.statemachine.settings.full.device.wipedevice

import androidx.compose.runtime.*
import build.wallet.di.ActivityScope
import build.wallet.di.BitkeyInject
import build.wallet.statemachine.core.ButtonDataModel
import build.wallet.statemachine.core.ScreenModel
import build.wallet.statemachine.core.SuccessBodyModel
import build.wallet.statemachine.settings.full.device.wipedevice.WipingDeviceUiState.*
import build.wallet.statemachine.settings.full.device.wipedevice.confirmation.WipingDeviceConfirmationProps
import build.wallet.statemachine.settings.full.device.wipedevice.confirmation.WipingDeviceConfirmationUiStateMachine
import build.wallet.statemachine.settings.full.device.wipedevice.intro.WipingDeviceIntroProps
import build.wallet.statemachine.settings.full.device.wipedevice.intro.WipingDeviceIntroUiStateMachine
import build.wallet.statemachine.settings.full.device.wipedevice.processing.WipingDeviceProgressProps
import build.wallet.statemachine.settings.full.device.wipedevice.processing.WipingDeviceProgressUiStateMachine

@BitkeyInject(ActivityScope::class)
class WipingDeviceUiStateMachineImpl(
  private val wipingDeviceIntroUiStateMachine: WipingDeviceIntroUiStateMachine,
  private val wipingDeviceConfirmationUiStateMachine: WipingDeviceConfirmationUiStateMachine,
  private val wipingDeviceProgressUiStateMachine: WipingDeviceProgressUiStateMachine,
) : WipingDeviceUiStateMachine {
  @Composable
  override fun model(props: WipingDeviceProps): ScreenModel {
    var uiState: WipingDeviceUiState by remember(props.wipeContext, props.initialStep) {
      mutableStateOf(
        props.initialUiState()
      )
    }

    return when (val state = uiState) {
      WipingDeviceIntroUiState -> {
        wipingDeviceIntroUiStateMachine.model(
          WipingDeviceIntroProps(
            onBack = props.onBack,
            onUnwindToMoneyHome = props.onSuccess,
            onDeviceConfirmed = { isDevicePaired, wipeContext ->
              uiState = WipingDeviceConfirmationUiState(isDevicePaired, wipeContext)
            },
            fullAccount = props.fullAccount,
            initialStep = props.initialStep,
            wipeContext = props.wipeContext
          )
        )
      }

      is WipingDeviceConfirmationUiState -> {
        wipingDeviceConfirmationUiStateMachine.model(
          WipingDeviceConfirmationProps(
            onBack = {
              when (props.wipeContext) {
                is WipeContext.InactiveDevice -> props.onBack()
                WipeContext.Default -> when (props.initialStep) {
                  WipingDeviceInitialStep.Intro -> uiState = WipingDeviceIntroUiState
                  WipingDeviceInitialStep.ScanDevice -> props.onBack()
                }
              }
            },
            onWipeDevice = {
              uiState = WipingDeviceProgressUiState
            },
            isDevicePaired = state.isDevicePaired,
            fullAccount = props.fullAccount,
            wipeContext = state.wipeContext
          )
        )
      }

      WipingDeviceProgressUiState -> {
        wipingDeviceProgressUiStateMachine.model(
          WipingDeviceProgressProps {
            uiState = WipingDeviceSuccessUiState
          }
        )
      }

      WipingDeviceSuccessUiState -> {
        SuccessBodyModel(
          id = WipingDeviceEventTrackerScreenId.RESET_DEVICE_SUCCESS,
          title = "Your Bitkey device is now wiped",
          message = "Your device has been wiped and can now be safely discarded or passed on.",
          primaryButtonModel = ButtonDataModel(text = "Done", onClick = props.onSuccess)
        ).asModalScreen()
      }
    }
  }

  private fun WipingDeviceProps.initialUiState(): WipingDeviceUiState =
    when (val context = wipeContext) {
      is WipeContext.InactiveDevice ->
        when (initialStep) {
          WipingDeviceInitialStep.ScanDevice -> WipingDeviceIntroUiState
          WipingDeviceInitialStep.Intro -> {
            // Skip intro and go directly to the wipe confirmation checklist with the inactive
            // device's hardware type.
            WipingDeviceConfirmationUiState(
              isDevicePaired = false,
              wipeContext = context
            )
          }
        }

      WipeContext.Default -> WipingDeviceIntroUiState
    }
}

private sealed interface WipingDeviceUiState {
  /**
   * Viewing the wipe device intro screen
   */
  data object WipingDeviceIntroUiState : WipingDeviceUiState

  /**
   * Viewing the wipe device confirmation screen
   */
  data class WipingDeviceConfirmationUiState(
    val isDevicePaired: Boolean,
    val wipeContext: WipeContext = WipeContext.Default,
  ) : WipingDeviceUiState

  /**
   * Viewing the wipe device progress screen
   */
  data object WipingDeviceProgressUiState : WipingDeviceUiState

  /**
   * Viewing the wipe device success screen
   */
  data object WipingDeviceSuccessUiState : WipingDeviceUiState
}
