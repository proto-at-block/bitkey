package build.wallet.statemachine.dev

import build.wallet.debug.cloud.CloudBackupStoreType
import build.wallet.statemachine.core.StateMachine
import build.wallet.ui.model.list.ListGroupModel

/**
 * State machine for showing debug options to delete app state: keys, account, backup, onboarding
 * states, etc.
 */
interface AppStateDeleterOptionsUiStateMachine : StateMachine<AppStateDeleterOptionsUiProps, ListGroupModel?>

/**
 * @property [onDeleteAppKeyRequest] called when "Delete App Key" is pressed.
 * @property [onDeleteAppKeyBackupRequest] called when "Delete App Key Backup" is pressed.
 * @property [onDeleteAppKeyAndBackupRequest] called when "Delete App Key and Backup" is pressed.
 * @property [onDeleteAllBackupsRequest] called when "Delete All App Key Backups" is pressed.
 * @property [onDeleteBackupsInStoreRequest] called when a store-specific backup delete row is pressed.
 * @property [onDeleteOnboardingAppKeyRequest] called when "Delete Onboarding App Key" is pressed.
 */
data class AppStateDeleterOptionsUiProps(
  val onDeleteAppKeyRequest: () -> Unit,
  val onDeleteAppKeyBackupRequest: () -> Unit,
  val onDeleteAppKeyAndBackupRequest: () -> Unit,
  val onDeleteAllBackupsRequest: () -> Unit,
  val onDeleteBackupsInStoreRequest: (CloudBackupStoreType) -> Unit,
  val onDeleteOnboardingAppKeyRequest: () -> Unit,
  val showDeleteAppKey: Boolean,
)
