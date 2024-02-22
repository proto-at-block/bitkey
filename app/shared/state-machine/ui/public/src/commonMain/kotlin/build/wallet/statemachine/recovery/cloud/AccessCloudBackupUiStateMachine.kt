package build.wallet.statemachine.recovery.cloud

import build.wallet.cloud.backup.CloudBackup
import build.wallet.cloud.store.CloudStoreAccount
import build.wallet.emergencyaccesskit.EmergencyAccessKitAssociation
import build.wallet.statemachine.core.ScreenModel
import build.wallet.statemachine.core.StateMachine

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
 * @property onBackupFound handler for when cloud backup is successfully found.
 * @property onCannotAccessCloudBackup handler for when customer is not able to access cloud storage,
 * or we are not able to find backup on the cloud storage.
 * @property showErrorOnBackupMissing Whether to display a screen if no backup can be loaded
 * or to immediately invoke [onCannotAccessCloudBackup] instead.
 */
data class AccessCloudBackupUiProps(
  val eakAssociation: EmergencyAccessKitAssociation,
  val forceSignOutFromCloud: Boolean,
  val onExit: () -> Unit,
  val onBackupFound: (backup: CloudBackup) -> Unit,
  val onCannotAccessCloudBackup: (CloudStoreAccount?) -> Unit,
  val onImportEmergencyAccessKit: () -> Unit,
  val showErrorOnBackupMissing: Boolean = true,
)
