package build.wallet.statemachine.settings.full.device.wipedevice

import androidx.compose.runtime.*
import build.wallet.bitcoin.balance.BitcoinBalance
import build.wallet.di.ActivityScope
import build.wallet.di.BitkeyInject
import build.wallet.statemachine.core.ScreenModel
import build.wallet.statemachine.settings.full.device.wipedevice.WipingDeviceUiState.*
import build.wallet.statemachine.settings.full.device.wipedevice.complete.WipingDeviceSuccessProps
import build.wallet.statemachine.settings.full.device.wipedevice.complete.WipingDeviceSuccessUiStateMachine
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
  private val wipingDeviceSuccessUiStateMachine: WipingDeviceSuccessUiStateMachine,
) : WipingDeviceUiStateMachine {
  @Composable
  override fun model(props: WipingDeviceProps): ScreenModel {
    var uiState: WipingDeviceUiState by remember {
      mutableStateOf(
        WipingDeviceIntroUiState(
          isShowingScanToContinueSheet = false
        )
      )
    }

    return when (val state = uiState) {
      is WipingDeviceIntroUiState -> {
        wipingDeviceIntroUiStateMachine.model(
          WipingDeviceIntroProps(
            onBack = props.onBack,
            onUnwindToMoneyHome = props.onSuccess,
            onDeviceConfirmed = { isDevicePaired ->
              uiState = WipingDeviceConfirmationUiState(isDevicePaired)
            },
            fullAccount = props.fullAccount
          )
        )
      }

      is WipingDeviceConfirmationUiState -> {
        wipingDeviceConfirmationUiStateMachine.model(
          WipingDeviceConfirmationProps(
            onBack = {
              uiState = WipingDeviceIntroUiState()
            },
            onWipeDevice = {
              uiState = WipingDeviceProgressUiState
            },
            isDevicePaired = state.isDevicePaired
          )
        )
      }

      is WipingDeviceProgressUiState -> {
        wipingDeviceProgressUiStateMachine.model(
          WipingDeviceProgressProps {
            uiState = WipingDeviceSuccessUiState
          }
        )
      }

      is WipingDeviceSuccessUiState -> {
        wipingDeviceSuccessUiStateMachine.model(
          WipingDeviceSuccessProps(onDone = props.onSuccess)
        )
      }
    }
  }
}

private sealed interface WipingDeviceUiState {
  /**
   * Viewing the wipe device intro screen
   */
  data class WipingDeviceIntroUiState(
    val isShowingScanToContinueSheet: Boolean = false,
    val isShowingTransferFundsSheet: Boolean = false,
    val balance: BitcoinBalance? = null,
  ) : WipingDeviceUiState

  /**
   * Viewing the wipe device confirmation screen
   */
  data class WipingDeviceConfirmationUiState(
    val isDevicePaired: Boolean,
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
