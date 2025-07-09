package build.wallet.statemachine.dev

import build.wallet.fwup.FirmwareData
import build.wallet.statemachine.core.StateMachine
import build.wallet.ui.model.list.ListGroupModel

/**
 * State machine for Bitkey dev options:
 * - viewing firmware metadata
 * - updating firmware
 * - wiping Bitkey
 * - enabling/disable NFC app haptics
 */
interface BitkeyDeviceOptionsUiStateMachine : StateMachine<BitkeyDeviceOptionsUiProps, ListGroupModel>

data class BitkeyDeviceOptionsUiProps(
  val firmwareData: FirmwareData,
  val onFirmwareMetadataClick: () -> Unit,
  val onFirmwareUpdateClick: (FirmwareData.FirmwareUpdateState.PendingUpdate) -> Unit,
  val onWipeBitkeyClick: () -> Unit,
)
