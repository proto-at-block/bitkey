package build.wallet.statemachine.data.recovery.lostapp

import build.wallet.cloud.backup.CloudBackup
import build.wallet.recovery.Recovery.StillRecovering
import build.wallet.statemachine.core.StateMachine

/**
 * Manages state of Lost App Recovery:
 * - initiation of Delay & Notify recovery
 * - waiting for Delay period
 * - completion of Delay & Notify recovery
 */
interface LostAppRecoveryDataStateMachine : StateMachine<LostAppRecoveryProps, LostAppRecoveryData>

/**
 * @property cloudBackups List of cloud backups to try during recovery. The restoration flow will
 * attempt to decrypt each backup with the hardware key until one succeeds.
 * @property fullAccountConfig keybox configuration to use for Lost App recovery.
 * @property account existing account if any. TODO(W-3072): move into state machine as implementation detail.
 */
data class LostAppRecoveryProps(
  val cloudBackups: List<CloudBackup>,
  val activeRecovery: StillRecovering?,
  val onRollback: () -> Unit,
  val goToLiteAccountCreation: () -> Unit,
)
