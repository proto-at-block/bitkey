package build.wallet.statemachine.data.recovery.verification

import build.wallet.bitkey.f8e.FullAccountId
import build.wallet.bitkey.factor.PhysicalFactor
import build.wallet.f8e.F8eEnvironment
import build.wallet.f8e.auth.HwFactorProofOfPossession
import build.wallet.statemachine.core.StateMachine

/**
 * Data state machine that manages the notification touchpoint verification flow
 * for a given recovery.
 */
interface RecoveryNotificationVerificationDataStateMachine :
  StateMachine<RecoveryNotificationVerificationDataProps, RecoveryNotificationVerificationData>

/**
 * @property f8eEnvironment F8e environment to use for the current recovery.
 * @property accountId Account ID to use for the current recovery.
 */
data class RecoveryNotificationVerificationDataProps(
  val f8eEnvironment: F8eEnvironment,
  val fullAccountId: FullAccountId,
  val onRollback: () -> Unit,
  val onComplete: () -> Unit,
  val hwFactorProofOfPossession: HwFactorProofOfPossession?,
  val lostFactor: PhysicalFactor,
)
