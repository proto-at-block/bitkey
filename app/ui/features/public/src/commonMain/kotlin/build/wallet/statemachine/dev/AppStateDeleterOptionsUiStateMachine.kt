package build.wallet.statemachine.dev

import build.wallet.statemachine.core.StateMachine
import build.wallet.ui.model.list.ListGroupModel

/**
 * State machine for showing debug options to delete app state: keys, account, backup, onboarding
 * states, etc.
 */
interface AppStateDeleterOptionsUiStateMachine : StateMachine<AppStateDeleterOptionsUiProps, ListGroupModel?>

/**
 * @property [onDeleteAppKeyRequest] called when "Delete App Key" is pressed.
 * @property [onDeleteAppKeyBackupRequest] called when "Delete App Key" is pressed.
 * @property [onDeleteAppKeyAndBackupRequest] called when "Delete App Key and Backup" is pressed.
 */
data class AppStateDeleterOptionsUiProps(
  val onDeleteAppKeyRequest: () -> Unit,
  val onDeleteAppKeyBackupRequest: () -> Unit,
  val onDeleteAppKeyAndBackupRequest: () -> Unit,
  val onDeleteAllBackupRequest: () -> Unit,
  val showDeleteAppKey: Boolean,
)
