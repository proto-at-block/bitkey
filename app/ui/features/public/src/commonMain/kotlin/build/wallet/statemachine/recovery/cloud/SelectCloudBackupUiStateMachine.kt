package build.wallet.statemachine.recovery.cloud

import build.wallet.cloud.backup.CloudBackup
import build.wallet.statemachine.core.ScreenModel
import build.wallet.statemachine.core.StateMachine

/**
 * Props for [SelectCloudBackupUiStateMachine].
 */
data class SelectCloudBackupUiProps(
  /**
   * List of cloud backups to display for selection.
   */
  val backups: List<CloudBackup>,
  /**
   * Callback invoked when a backup is selected.
   */
  val onBackupSelected: (CloudBackup) -> Unit,
  /**
   * Callback invoked when the back button is pressed.
   */
  val onBack: () -> Unit,
)

/**
 * State machine for selecting a cloud backup from multiple available backups.
 * Manages displaying the backup selection screen and the Learn More webview.
 */
interface SelectCloudBackupUiStateMachine :
  StateMachine<SelectCloudBackupUiProps, ScreenModel>
