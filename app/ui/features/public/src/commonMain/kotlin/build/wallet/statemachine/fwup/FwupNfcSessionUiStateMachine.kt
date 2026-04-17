package build.wallet.statemachine.fwup

import bitkey.account.HardwareType
import build.wallet.fwup.McuFwupData
import build.wallet.nfc.NfcException
import build.wallet.statemachine.core.ScreenModel
import build.wallet.statemachine.core.StateMachine
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf

/**
 * State machine managing the NFC session for FWUP and vending UI screen models
 * for different session states. Similar to [NfcSessionUiStateMachine] which is
 * used for all other NFC transactions.
 */
interface FwupNfcSessionUiStateMachine :
  StateMachine<FwupNfcSessionUiProps, ScreenModel>

/**
 * @property transactionType: Whether a FWUP was in progress or should start from the beginning.
 * @property selectedMcuUpdates: List of specific MCU updates to apply.
 *   When empty, the default behavior is used (all pending MCU updates from
 *   [FirmwareDataService]). When non-empty, only the specified MCUs will be updated.
 * @property hardwareTypeOverride: When provided, overrides the hardware type detection
 *   from account config. Used by debug menu when real hardware is enabled.
 * @property onBack: Callback for exiting the NFC session without completion (i.e. user taps 'Cancel').
 * @property onDone: Callback for exiting the NFC session after completion.
 * @property onError: Error callback so that we can show the error as a half-sheet on
 * the instructional screen vended by [FwupNfcUiStateMachine]
 */
data class FwupNfcSessionUiProps(
  val transactionType: FwupTransactionType,
  val selectedMcuUpdates: ImmutableList<McuFwupData> = persistentListOf(),
  val hardwareTypeOverride: HardwareType? = null,
  val showNativeSheetOnIos: Boolean = false,
  val onBack: () -> Unit,
  val onDone: () -> Unit,
  val onError: (
    error: NfcException,
    updateWasInProgress: Boolean,
    transactionType: FwupTransactionType,
  ) -> Unit,
)
