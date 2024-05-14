package build.wallet.statemachine.settings.full.device.fingerprints

import build.wallet.firmware.EnrolledFingerprints
import build.wallet.firmware.FingerprintHandle
import build.wallet.statemachine.core.SheetModel
import build.wallet.statemachine.core.StateMachine

/**
 * State machine for editing the label for an enrolled fingerprint or removing the fingerprint
 * altogether.
 */
interface EditingFingerprintUiStateMachine : StateMachine<EditingFingerprintProps, SheetModel>

data class EditingFingerprintProps(
  val enrolledFingerprints: EnrolledFingerprints,
  val onBack: () -> Unit,
  val onSave: (fingerprintHandle: FingerprintHandle) -> Unit,
  val onDeleteFingerprint: (fingerprintHandle: FingerprintHandle) -> Unit,
  val fingerprintToEdit: FingerprintHandle,
  val isExistingFingerprint: Boolean,
)
