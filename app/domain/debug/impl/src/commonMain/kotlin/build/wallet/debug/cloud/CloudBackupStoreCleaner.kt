package build.wallet.debug.cloud

import build.wallet.cloud.store.CloudStoreAccount
import com.github.michaelbull.result.Result

/**
 * Platform-specific remote cleanup behavior for debug cloud backup deletion.
 *
 * The debug menu uses this to delete keys directly from platform storage backends:
 * - Android: cloud key-value store (Google Drive app-data).
 * - JVM: cloud backup store (local fake backup store).
 * - iOS: Ubiquitous KVS and/or CloudKit key-value store.
 */
interface CloudBackupStoreCleaner {
  suspend fun deleteBackupsIn(
    type: CloudBackupStoreType,
    cloudStoreAccount: CloudStoreAccount,
  ): Result<Unit, Throwable>
}
