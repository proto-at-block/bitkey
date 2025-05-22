package build.wallet.cloud.backup.socrec

import build.wallet.worker.AppWorker
import kotlinx.coroutines.flow.StateFlow
import kotlinx.datetime.Instant

/**
 * Performs background cloud backup when information about Recovery Contacts changes, i.e.
 * some are added/removed.
 *
 * Monitors locally stored cloud backups and synced Relationships to determine
 * if the cloud backup should be refreshed and if so, uploads a new cloud backup.
 */
interface SocRecCloudBackupSyncWorker : AppWorker {
  /**
   * The timestamp that last check for cloud backup refresh completed, successfully or otherwise.
   * Primarily used for testing purposes.
   */
  val lastCheck: StateFlow<Instant>
}
