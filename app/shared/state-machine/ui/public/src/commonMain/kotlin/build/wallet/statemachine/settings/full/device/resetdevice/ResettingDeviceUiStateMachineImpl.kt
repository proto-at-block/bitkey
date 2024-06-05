package build.wallet.statemachine.settings.full.device.resetdevice

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import build.wallet.bitcoin.balance.BitcoinBalance
import build.wallet.statemachine.core.ScreenModel
import build.wallet.statemachine.settings.full.device.resetdevice.ResettingDeviceUiState.ResettingDeviceConfirmationUiState
import build.wallet.statemachine.settings.full.device.resetdevice.ResettingDeviceUiState.ResettingDeviceIntroUiState
import build.wallet.statemachine.settings.full.device.resetdevice.ResettingDeviceUiState.ResettingDeviceProgressUiState
import build.wallet.statemachine.settings.full.device.resetdevice.ResettingDeviceUiState.ResettingDeviceSuccessUiState
import build.wallet.statemachine.settings.full.device.resetdevice.confirmation.ResettingDeviceConfirmationProps
import build.wallet.statemachine.settings.full.device.resetdevice.confirmation.ResettingDeviceConfirmationUiStateMachine
import build.wallet.statemachine.settings.full.device.resetdevice.intro.ResettingDeviceIntroProps
import build.wallet.statemachine.settings.full.device.resetdevice.intro.ResettingDeviceIntroUiStateMachine

class ResettingDeviceUiStateMachineImpl(
  private val resettingDeviceIntroUiStateMachine: ResettingDeviceIntroUiStateMachine,
  private val resettingDeviceConfirmationUiStateMachine: ResettingDeviceConfirmationUiStateMachine,
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

    return when (uiState) {
      is ResettingDeviceIntroUiState -> {
        resettingDeviceIntroUiStateMachine.model(
          ResettingDeviceIntroProps(
            onBack = props.onBack,
            onUnwindToMoneyHome = props.onUnwindToMoneyHome,
            onDeviceConfirmed = {
              uiState = ResettingDeviceConfirmationUiState
            },
            spendingWallet = props.spendingWallet,
            keybox = props.keybox,
            balance = props.balance,
            isHardwareFake = props.isHardwareFake
          )
        )
      }

      is ResettingDeviceConfirmationUiState -> {
        resettingDeviceConfirmationUiStateMachine.model(
          ResettingDeviceConfirmationProps(
            onBack = props.onBack,
            onConfirmResetDevice = {
              uiState = ResettingDeviceProgressUiState
            }
          )
        )
      }

      ResettingDeviceProgressUiState -> TODO()
      ResettingDeviceSuccessUiState -> TODO()
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
  data object ResettingDeviceConfirmationUiState : ResettingDeviceUiState

  /**
   * Viewing the reset device progress screen
   */
  data object ResettingDeviceProgressUiState : ResettingDeviceUiState

  /**
   * Viewing the reset device success screen
   */
  data object ResettingDeviceSuccessUiState : ResettingDeviceUiState
}
