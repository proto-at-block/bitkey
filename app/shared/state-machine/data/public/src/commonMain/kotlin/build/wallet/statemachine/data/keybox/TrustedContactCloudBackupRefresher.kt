package build.wallet.statemachine.data.keybox

import build.wallet.bitkey.account.FullAccount
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.StateFlow
import kotlinx.datetime.Instant

/**
 * Performs background cloud backup when information about Trusted Contacts changes, i.e.
 * some are added/removed.
 */
interface TrustedContactCloudBackupRefresher {
  /**
   * Monitors locally stored cloud backups and synced SocRecRelationships to determine
   * if the cloud backup should be refreshed and if so, uploads a new cloud backup.
   */
  suspend fun refreshCloudBackupsWhenNecessary(
    scope: CoroutineScope,
    fullAccount: FullAccount,
  )

  /**
   * The timestamp that last check for cloud backup refresh completed, successfully or otherwise.
   */
  val lastCheck: StateFlow<Instant>
}
