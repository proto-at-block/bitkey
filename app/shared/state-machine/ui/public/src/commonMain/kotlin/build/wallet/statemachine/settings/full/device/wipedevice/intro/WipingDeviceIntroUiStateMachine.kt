package build.wallet.statemachine.settings.full.device.wipedevice.intro

import build.wallet.bitkey.account.FullAccount
import build.wallet.bitkey.account.FullAccountConfig
import build.wallet.statemachine.core.ScreenModel
import build.wallet.statemachine.core.StateMachine

/**
 * State machine to the intro screen, and its various modals for wiping a Bitkey device.
 */
interface WipingDeviceIntroUiStateMachine : StateMachine<WipingDeviceIntroProps, ScreenModel>

data class WipingDeviceIntroProps(
  val onBack: () -> Unit,
  val onUnwindToMoneyHome: () -> Unit,
  val onDeviceConfirmed: (pairedDevice: Boolean) -> Unit,
  val fullAccountConfig: FullAccountConfig,
  val fullAccount: FullAccount?,
)
