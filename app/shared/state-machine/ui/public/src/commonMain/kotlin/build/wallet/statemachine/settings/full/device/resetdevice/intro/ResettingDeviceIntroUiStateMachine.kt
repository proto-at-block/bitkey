package build.wallet.statemachine.settings.full.device.resetdevice.intro

import build.wallet.bitkey.account.FullAccount
import build.wallet.bitkey.account.FullAccountConfig
import build.wallet.statemachine.core.ScreenModel
import build.wallet.statemachine.core.StateMachine

/**
 * State machine to the intro screen, and it's various modals for resetting a Bitkey device.
 */
interface ResettingDeviceIntroUiStateMachine : StateMachine<ResettingDeviceIntroProps, ScreenModel>

data class ResettingDeviceIntroProps(
  val onBack: () -> Unit,
  val onUnwindToMoneyHome: () -> Unit,
  val onDeviceConfirmed: (pairedDevice: Boolean) -> Unit,
  val fullAccountConfig: FullAccountConfig,
  val fullAccount: FullAccount?,
)
