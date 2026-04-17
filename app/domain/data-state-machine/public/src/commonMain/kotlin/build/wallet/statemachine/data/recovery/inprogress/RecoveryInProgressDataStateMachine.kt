package build.wallet.statemachine.data.recovery.inprogress

import build.wallet.bitkey.app.AppGlobalAuthKey
import build.wallet.crypto.PublicKey
import build.wallet.recovery.Recovery.StillRecovering
import build.wallet.statemachine.core.StateMachine

/**
 * Data state machine for DN Lost App or Hw recovery which is currently in progress or
 * is completing.
 *
 * Covers:
 * - Recovery delay period
 * - Recovery cancellation
 * - Recovery completion
 */
interface RecoveryInProgressDataStateMachine :
  StateMachine<RecoveryInProgressProps, RecoveryInProgressData>

data class RecoveryInProgressProps(
  val recovery: StillRecovering,
  val oldAppGlobalAuthKey: PublicKey<AppGlobalAuthKey>?,
)
