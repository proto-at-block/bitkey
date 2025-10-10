package build.wallet.statemachine.recovery.cloud

import build.wallet.cloud.backup.CloudBackup
import build.wallet.statemachine.core.ScreenModel
import build.wallet.statemachine.core.StateMachine

/**
 * State machine that handles restoring a keybox and setting the active account from a cloud backup.
 */
interface FullAccountCloudBackupRestorationUiStateMachine :
  StateMachine<FullAccountCloudBackupRestorationUiProps, ScreenModel>

/**
 * @property backup the backup object to use to restore from.
 * @property onExit handler for when the flow needs to be exited completely.
 * @property onRecoverAppKey handler when the app key needs to be recovered using D&N
 * (in case if cloud backup is found but is corrupted/unusable, see W-11398).
 */
data class FullAccountCloudBackupRestorationUiProps(
  val backup: CloudBackup,
  val onRecoverAppKey: () -> Unit,
  val onExit: () -> Unit,
  val goToLiteAccountCreation: () -> Unit,
)
