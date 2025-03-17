package build.wallet.statemachine.fwup

import build.wallet.fwup.FirmwareData
import build.wallet.nfc.NfcException
import build.wallet.statemachine.core.ScreenModel
import build.wallet.statemachine.core.StateMachine

/**
 * State machine managing the NFC session for FWUP and vending UI screen models
 * for different session states. Similar to [NfcSessionUiStateMachine] which is
 * used for all other NFC transactions.
 */
interface FwupNfcSessionUiStateMachine :
  StateMachine<FwupNfcSessionUiProps, ScreenModel>

/**
 * @property firmwareData: Data for the firmware update.
 * @property transactionType: Whether a FWUP was in progress or should start from the beginning.
 * @property onBack: Callback for exiting the NFC session without completion (i.e. user taps 'Cancel').
 * @property onDone: Callback for exiting the NFC session after completion.
 * @property onError: Error callback so that we can show the error as a half-sheet on
 * the instructional screen vended by [FwupNfcUiStateMachine]
 */
data class FwupNfcSessionUiProps(
  val firmwareData: FirmwareData.FirmwareUpdateState.PendingUpdate,
  val transactionType: FwupTransactionType,
  val onBack: () -> Unit,
  val onDone: () -> Unit,
  val onError: (
    error: NfcException,
    updateWasInProgress: Boolean,
    transactionType: FwupTransactionType,
  ) -> Unit,
)
