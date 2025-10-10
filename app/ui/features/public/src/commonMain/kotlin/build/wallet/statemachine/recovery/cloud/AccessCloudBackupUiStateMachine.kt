package build.wallet.statemachine.recovery.cloud

import build.wallet.cloud.backup.CloudBackup
import build.wallet.cloud.store.CloudStoreAccount
import build.wallet.statemachine.core.ScreenModel
import build.wallet.statemachine.core.StateMachine
import build.wallet.statemachine.data.keybox.AccountData.StartIntent

/**
 * State machine that handles logging into a cloud storage and looking up [CloudBackup] on cloud
 * storage.
 */
interface AccessCloudBackupUiStateMachine : StateMachine<AccessCloudBackupUiProps, ScreenModel>

/**
 * @property forceSignOutFromCloud if true, we will force sign out from cloud storage. For example,
 * when user was not able to find a backup on one of the accounts, went back to the previous screen
 * or closed the app, we want to customer to see account picker again instead of reusing the same
 * (Google) account.
 * @property onExit handler for when the flow needs to be exited completely.
 * @property onCannotAccessCloudBackup handler for when customer is not able to access cloud storage,
 * or we are not able to find backup on the cloud storage.
 * @property showErrorOnBackupMissing Whether to display a screen if no backup can be loaded
 * or to immediately invoke [onCannotAccessCloudBackup] instead.
 */
data class AccessCloudBackupUiProps(
  val startIntent: StartIntent,
  val inviteCode: String?,
  val onExit: () -> Unit,
  val onStartCloudRecovery: (cloudStoreAccount: CloudStoreAccount, backup: CloudBackup) -> Unit,
  val onStartLiteAccountRecovery: (backup: CloudBackup) -> Unit,
  val onStartLostAppRecovery: () -> Unit,
  val onStartLiteAccountCreation: (String?, StartIntent) -> Unit,
  val onImportEmergencyExitKit: () -> Unit,
  val showErrorOnBackupMissing: Boolean,
)
