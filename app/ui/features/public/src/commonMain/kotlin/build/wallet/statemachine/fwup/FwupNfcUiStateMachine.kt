package build.wallet.statemachine.fwup

import bitkey.account.HardwareType
import build.wallet.fwup.McuFwupData
import build.wallet.statemachine.core.ScreenModel
import build.wallet.statemachine.core.StateMachine
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf

/**
 * State machine for managing the UI for the entire FWUP experience,
 * which includes the initial informational screen and then the NFC
 * session screens which is managed by [FwupNfcSessionUiStateMachine]
 */
interface FwupNfcUiStateMachine :
  StateMachine<FwupNfcUiProps, ScreenModel>

/**
 * @property onDone Callback when the firmware update is complete or cancelled.
 * @property selectedMcuUpdates List of specific MCU updates to apply.
 *   When empty, the default behavior is used (all pending MCU updates from
 *   [FirmwareDataService]). When non-empty, only the specified MCUs will be updated.
 * @property hardwareTypeOverride When provided, overrides the hardware type detection
 *   from account config. Used by debug menu when real hardware is enabled but we need
 *   to derive hardware type from the firmware update itself.
 */
data class FwupNfcUiProps(
  val onDone: () -> Unit,
  val selectedMcuUpdates: ImmutableList<McuFwupData> = persistentListOf(),
  val hardwareTypeOverride: HardwareType? = null,
  val showNativeSheetOnIos: Boolean = false,
)
