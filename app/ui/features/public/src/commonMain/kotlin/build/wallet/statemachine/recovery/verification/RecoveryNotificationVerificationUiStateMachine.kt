package build.wallet.statemachine.recovery.verification

import build.wallet.bitkey.f8e.FullAccountId
import build.wallet.bitkey.factor.PhysicalFactor
import build.wallet.f8e.auth.HwFactorProofOfPossession
import build.wallet.recovery.Recovery
import build.wallet.statemachine.core.AppSegment
import build.wallet.statemachine.core.ScreenModel
import build.wallet.statemachine.core.StateMachine

/**
 * Flow used for verifying a notification touchpoint (either sms or email) for recovery.
 */
interface RecoveryNotificationVerificationUiStateMachine :
  StateMachine<RecoveryNotificationVerificationUiProps, ScreenModel>

data class RecoveryNotificationVerificationUiProps(
  val fullAccountId: FullAccountId,
  /**
   * If the user is performing a local recovery, [localLostFactor] will be set to the factor of that
   * recovery type. If the user is verifying a touchpoint due contesting a server recovery,
   * e.g. [Recovery.SomeoneElseIsRecovering], [localLostFactor] will be null as the there is no local
   * recovery occurring.
   */
  val localLostFactor: PhysicalFactor?,
  // TODO: BKR-1117: make non-nullable
  val segment: AppSegment? = null,
  val actionDescription: String? = null,
  val hwFactorProofOfPossession: HwFactorProofOfPossession?,
  val onRollback: () -> Unit,
  val onComplete: () -> Unit,
)
