package build.wallet.statemachine.settings.full.device.fingerprints.resetfingerprints

import build.wallet.statemachine.core.ScreenModel
import build.wallet.statemachine.core.StateMachine
import kotlinx.datetime.Instant

/**
 * State machine for resetting device fingerprints using D+N.
 */
interface ResetFingerprintsUiStateMachine : StateMachine<ResetFingerprintsProps, ScreenModel>

/**
 * Information about a pending fingerprint reset action.
 */
data class PendingFingerprintResetInfo(
  val actionId: String,
  val startTime: Instant,
  val endTime: Instant,
  val completionToken: String,
  val cancellationToken: String,
)

data class ResetFingerprintsProps(
  val onComplete: () -> Unit,
  val onCancel: () -> Unit,
  val initialPendingActionInfo: PendingFingerprintResetInfo? = null,
)
