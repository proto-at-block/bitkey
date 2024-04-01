package build.wallet.statemachine.recovery.verification

import build.wallet.bitkey.factor.PhysicalFactor
import build.wallet.statemachine.core.AppSegment
import build.wallet.statemachine.core.ScreenModel
import build.wallet.statemachine.core.StateMachine
import build.wallet.statemachine.data.recovery.verification.RecoveryNotificationVerificationData

/**
 * Flow used for verifying a notification touchpoint (either sms or email) for recovery.
 */
interface RecoveryNotificationVerificationUiStateMachine :
  StateMachine<RecoveryNotificationVerificationUiProps, ScreenModel>

data class RecoveryNotificationVerificationUiProps(
  val recoveryNotificationVerificationData: RecoveryNotificationVerificationData,
  val lostFactor: PhysicalFactor,
  // TODO: BKR-1117: make non-nullable
  val segment: AppSegment? = null,
  val actionDescription: String? = null,
)
