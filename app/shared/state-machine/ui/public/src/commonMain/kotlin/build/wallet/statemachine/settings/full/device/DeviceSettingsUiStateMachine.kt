package build.wallet.statemachine.settings.full.device

import build.wallet.statemachine.core.ScreenModel
import build.wallet.statemachine.core.StateMachine
import build.wallet.statemachine.data.firmware.FirmwareData
import build.wallet.statemachine.data.keybox.AccountData.HasActiveFullAccountData.ActiveFullAccountLoadedData

/**
 * State machine for showing device information and various actions to take on hardware
 */
interface DeviceSettingsUiStateMachine : StateMachine<DeviceSettingsProps, ScreenModel>

/**
 * Device settings props
 *
 * @property accountData - The current active keybox
 * @property onBack - invoked once a back action has occurred
 */
data class DeviceSettingsProps(
  val accountData: ActiveFullAccountLoadedData,
  val firmwareData: FirmwareData,
  val onBack: () -> Unit,
)
