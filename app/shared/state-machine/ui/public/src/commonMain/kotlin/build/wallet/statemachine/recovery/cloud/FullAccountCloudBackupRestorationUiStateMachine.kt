package build.wallet.statemachine.recovery.cloud

import build.wallet.bitkey.keybox.KeyboxConfig
import build.wallet.cloud.backup.CloudBackup
import build.wallet.statemachine.core.ScreenModel
import build.wallet.statemachine.core.StateMachine

/**
 * State machine that handles restoring a keybox and setting the active account from a cloud backup.
 */
interface FullAccountCloudBackupRestorationUiStateMachine : StateMachine<FullAccountCloudBackupRestorationUiProps, ScreenModel>

/**
 * @property keyboxConfig current [KeyboxConfig]
 * @property backup the backup object to use to restore from.
 * @property onExit handler for when the flow needs to be exited completely.
 * @property onKeyboxRestoredFromBackup handler for when cloud backup is successfully found and
 * used to restore the account.
 */
data class FullAccountCloudBackupRestorationUiProps(
  val keyboxConfig: KeyboxConfig,
  val backup: CloudBackup,
  val onExit: () -> Unit,
)
