package build.wallet.statemachine.settings.full.device.wipedevice.confirmation

import build.wallet.bitkey.account.FullAccount
import build.wallet.statemachine.core.ScreenModel
import build.wallet.statemachine.core.StateMachine
import build.wallet.statemachine.settings.full.device.wipedevice.WipeContext

/**
 * State machine for the confirmation screen of the wipe device flow.
 */
interface WipingDeviceConfirmationUiStateMachine :
  StateMachine<WipingDeviceConfirmationProps, ScreenModel>

data class WipingDeviceConfirmationProps(
  val onBack: () -> Unit,
  val onWipeDevice: () -> Unit,
  val isDevicePaired: Boolean,
  val fullAccount: FullAccount?,
  /**
   * Context for the wipe operation.
   * When [WipeContext.W3UpgradeOldDevice], skips confirmation checkboxes and uses the old
   * hardware type for NFC commands.
   */
  val wipeContext: WipeContext = WipeContext.Default,
)
