package build.wallet.cloud.backup

import build.wallet.bitkey.f8e.AccountId
import build.wallet.cloud.store.CloudStoreAccount
import com.github.michaelbull.result.Result

interface CloudBackupRepository {
  /**
   * Access and decode [CloudBackup] from cloud storage using logged in [CloudStoreAccount].
   */
  suspend fun readBackup(
    cloudStoreAccount: CloudStoreAccount,
  ): Result<CloudBackup?, CloudBackupError>

  /**
   * Encode and store [CloudBackup] on cloud storage using logged in [CloudStoreAccount].
   */
  suspend fun writeBackup(
    accountId: AccountId,
    cloudStoreAccount: CloudStoreAccount,
    backup: CloudBackup,
  ): Result<Unit, CloudBackupError>

  /**
   * Clear wallet from cloud storage using logged in [CloudStoreAccount].
   */
  suspend fun clear(cloudStoreAccount: CloudStoreAccount): Result<Unit, CloudBackupError>
}
