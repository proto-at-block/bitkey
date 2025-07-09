package build.wallet.statemachine.settings.full.device.fingerprints.resetfingerprints

import build.wallet.bitkey.account.FullAccount
import build.wallet.statemachine.core.ScreenModel
import build.wallet.statemachine.core.StateMachine

/**
 * State machine for resetting device fingerprints using D+N.
 */
interface ResetFingerprintsUiStateMachine : StateMachine<ResetFingerprintsProps, ScreenModel>

/**
 * Properties for the ResetFingerprintsUiStateMachine.
 *
 * @property onComplete Callback invoked when the fingerprint reset process is completed.
 * @property onCancel Callback invoked when the user cancels the fingerprint reset process.
 * @property onFwUpRequired Callback invoked when firmware update is required to support fingerprint reset.
 * @property account The current full account.
 */
data class ResetFingerprintsProps(
  val onComplete: () -> Unit,
  val onCancel: () -> Unit,
  val onFwUpRequired: () -> Unit,
  val account: FullAccount,
)
