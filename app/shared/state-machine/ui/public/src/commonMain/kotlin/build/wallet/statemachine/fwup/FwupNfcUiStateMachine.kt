package build.wallet.statemachine.fwup

import build.wallet.statemachine.core.ScreenModel
import build.wallet.statemachine.core.StateMachine
import build.wallet.statemachine.data.firmware.FirmwareData

/**
 * State machine for managing the UI for the entire FWUP experience,
 * which includes the initial informational screen and then the NFC
 * session screens which is managed by [FwupNfcSessionUiStateMachine]
 */
interface FwupNfcUiStateMachine :
  StateMachine<FwupNfcUiProps, ScreenModel>

data class FwupNfcUiProps(
  val firmwareData: FirmwareData.FirmwareUpdateState.PendingUpdate,
  val onDone: () -> Unit,
  val isHardwareFake: Boolean,
)
