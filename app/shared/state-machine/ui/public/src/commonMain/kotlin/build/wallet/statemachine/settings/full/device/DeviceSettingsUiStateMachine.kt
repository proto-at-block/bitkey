package build.wallet.statemachine.settings.full.device

import build.wallet.bitkey.account.FullAccount
import build.wallet.statemachine.core.ScreenModel
import build.wallet.statemachine.core.StateMachine
import build.wallet.statemachine.data.recovery.losthardware.LostHardwareRecoveryData

/**
 * State machine for showing device information and various actions to take on hardware
 */
interface DeviceSettingsUiStateMachine : StateMachine<DeviceSettingsProps, ScreenModel>

/**
 * Device settings props
 */
data class DeviceSettingsProps(
  val account: FullAccount,
  val lostHardwareRecoveryData: LostHardwareRecoveryData,
  val onBack: () -> Unit,
  val onUnwindToMoneyHome: () -> Unit,
)
