package build.wallet.statemachine.settings.full.device.resetdevice

import androidx.compose.runtime.*
import build.wallet.bitcoin.balance.BitcoinBalance
import build.wallet.statemachine.core.ScreenModel
import build.wallet.statemachine.settings.full.device.resetdevice.ResettingDeviceUiState.*
import build.wallet.statemachine.settings.full.device.resetdevice.complete.ResettingDeviceSuccessProps
import build.wallet.statemachine.settings.full.device.resetdevice.complete.ResettingDeviceSuccessUiStateMachine
import build.wallet.statemachine.settings.full.device.resetdevice.confirmation.ResettingDeviceConfirmationProps
import build.wallet.statemachine.settings.full.device.resetdevice.confirmation.ResettingDeviceConfirmationUiStateMachine
import build.wallet.statemachine.settings.full.device.resetdevice.intro.ResettingDeviceIntroProps
import build.wallet.statemachine.settings.full.device.resetdevice.intro.ResettingDeviceIntroUiStateMachine
import build.wallet.statemachine.settings.full.device.resetdevice.processing.ResettingDeviceProgressProps
import build.wallet.statemachine.settings.full.device.resetdevice.processing.ResettingDeviceProgressUiStateMachine

class ResettingDeviceUiStateMachineImpl(
  private val resettingDeviceIntroUiStateMachine: ResettingDeviceIntroUiStateMachine,
  private val resettingDeviceConfirmationUiStateMachine: ResettingDeviceConfirmationUiStateMachine,
  private val resettingDeviceProgressUiStateMachine: ResettingDeviceProgressUiStateMachine,
  private val resettingDeviceSuccessUiStateMachine: ResettingDeviceSuccessUiStateMachine,
) : ResettingDeviceUiStateMachine {
  @Composable
  override fun model(props: ResettingDeviceProps): ScreenModel {
    var uiState: ResettingDeviceUiState by remember {
      mutableStateOf(
        ResettingDeviceIntroUiState(
          isShowingScanToContinueSheet = false
        )
      )
    }

    return when (val state = uiState) {
      is ResettingDeviceIntroUiState -> {
        resettingDeviceIntroUiStateMachine.model(
          ResettingDeviceIntroProps(
            onBack = props.onBack,
            onUnwindToMoneyHome = props.onSuccess,
            onDeviceConfirmed = { isDevicePaired ->
              uiState = ResettingDeviceConfirmationUiState(isDevicePaired)
            },
            fullAccountConfig = props.fullAccountConfig,
            fullAccount = props.fullAccount
          )
        )
      }

      is ResettingDeviceConfirmationUiState -> {
        resettingDeviceConfirmationUiStateMachine.model(
          ResettingDeviceConfirmationProps(
            onBack = {
              uiState = ResettingDeviceIntroUiState()
            },
            onResetDevice = {
              uiState = ResettingDeviceProgressUiState
            },
            isDevicePaired = state.isDevicePaired,
            isHardwareFake = props.fullAccountConfig.isHardwareFake
          )
        )
      }

      is ResettingDeviceProgressUiState -> {
        resettingDeviceProgressUiStateMachine.model(
          ResettingDeviceProgressProps {
            uiState = ResettingDeviceSuccessUiState
          }
        )
      }

      is ResettingDeviceSuccessUiState -> {
        resettingDeviceSuccessUiStateMachine.model(
          ResettingDeviceSuccessProps(onDone = props.onSuccess)
        )
      }
    }
  }
}

private sealed interface ResettingDeviceUiState {
  /**
   * Viewing the reset device intro screen
   */
  data class ResettingDeviceIntroUiState(
    val isShowingScanToContinueSheet: Boolean = false,
    val isShowingTransferFundsSheet: Boolean = false,
    val balance: BitcoinBalance? = null,
  ) : ResettingDeviceUiState

  /**
   * Viewing the reset device confirmation screen
   */
  data class ResettingDeviceConfirmationUiState(
    val isDevicePaired: Boolean,
  ) : ResettingDeviceUiState

  /**
   * Viewing the reset device progress screen
   */
  data object ResettingDeviceProgressUiState : ResettingDeviceUiState

  /**
   * Viewing the reset device success screen
   */
  data object ResettingDeviceSuccessUiState : ResettingDeviceUiState
}
