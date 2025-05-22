package build.wallet.statemachine.settings.full.device.fingerprints.resetfingerprints

import build.wallet.statemachine.core.ScreenModel
import build.wallet.statemachine.core.StateMachine

/**
 * State machine for resetting device fingerprints using D+N.
 */
interface ResetFingerprintsUiStateMachine : StateMachine<ResetFingerprintsProps, ScreenModel>

data class ResetFingerprintsProps(
  val onComplete: () -> Unit,
  val onCancel: () -> Unit,
)
