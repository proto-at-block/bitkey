package build.wallet.statemachine.settings.full.device.wipedevice

import build.wallet.bitkey.account.FullAccount
import build.wallet.statemachine.core.ScreenModel
import build.wallet.statemachine.core.StateMachine

/**
 * State machine to present screens for wiping a Bitkey device.
 */
interface WipingDeviceUiStateMachine : StateMachine<WipingDeviceProps, ScreenModel>

data class WipingDeviceProps(
  val onBack: () -> Unit,
  val onSuccess: () -> Unit,
  val fullAccount: FullAccount,
)
