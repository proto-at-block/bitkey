package build.wallet.cloud.backup

import build.wallet.cloud.backup.CloudBackupRestorer.CloudBackupRestorerError
import build.wallet.cloud.backup.FullAccountCloudBackupRestorer.AccountRestoration
import build.wallet.cloud.backup.v2.FullAccountKeys
import com.github.michaelbull.result.Err
import com.github.michaelbull.result.Result

class CloudBackupRestorerMock : CloudBackupRestorer {
  var result: Result<AccountRestoration, CloudBackupRestorerError> =
    Err(CloudBackupRestorerError.PkekMissingError)

  override suspend fun restore(
    cloudBackup: CloudBackup,
  ): Result<AccountRestoration, CloudBackupRestorerError> {
    return result
  }

  override suspend fun restoreWithDecryptedKeys(
    cloudBackup: CloudBackup,
    keysInfo: FullAccountKeys,
  ): Result<AccountRestoration, CloudBackupRestorerError> {
    return result
  }

  override suspend fun decryptCloudBackup(
    cloudBackup: CloudBackup,
  ): Result<FullAccountKeys, CloudBackupRestorerError> {
    error("Not implemented in mock")
  }

  fun reset() {
    result = Err(CloudBackupRestorerError.PkekMissingError)
  }
}
