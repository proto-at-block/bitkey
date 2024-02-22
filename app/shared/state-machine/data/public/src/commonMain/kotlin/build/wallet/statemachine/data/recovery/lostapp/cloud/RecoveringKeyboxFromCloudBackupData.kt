package build.wallet.statemachine.data.recovery.lostapp.cloud

import build.wallet.cloud.backup.CloudBackup

/**
 * Indicates that we are attempting to perform Cloud Recovery for Lost App.
 *
 * Note that currently logic lives in a UI state machine, so states are moved via callback by
 * the UI state machine.
 *
 * TODO(W-3756): migrate logic from UI to data state machine.
 */
sealed interface RecoveringKeyboxFromCloudBackupData {
  /**
   * Indicates that we are attempting to access cloud storage and read cloud backup for the keybox.
   *
   * @property onCloudBackupFound called by the UI state machine when a cloud backup was found
   * successfully. Moves state to [RecoveringFromCloudBackupData].
   * @property onCloudBackupNotAvailable called by the UI state machine when cloud backup was not found.
   * @property rollback rolls back state machine, in this case exiting.
   */
  data class AccessingCloudBackupData(
    val onCloudBackupFound: (CloudBackup) -> Unit,
    val onCloudBackupNotAvailable: () -> Unit,
    val onImportEmergencyAccessKit: () -> Unit,
    val rollback: () -> Unit,
  ) : RecoveringKeyboxFromCloudBackupData

  /**
   * Indicates that we are attempting to recover keybox from a cloud backup that we found earlier.
   *
   * @property cloudBackup the cloud backup instance that we are trying to recover keybox from.
   * @property rollback moves state back to [AccessingCloudBackupData].
   */
  data class RecoveringFromCloudBackupData(
    val cloudBackup: CloudBackup,
    val rollback: () -> Unit,
  ) : RecoveringKeyboxFromCloudBackupData
}
