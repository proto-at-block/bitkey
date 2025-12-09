package build.wallet.cloud.backup

import build.wallet.cloud.backup.CloudBackupV3Restorer.CloudBackupV3RestorerError
import build.wallet.cloud.backup.FullAccountCloudBackupRestorer.AccountRestoration
import build.wallet.cloud.backup.v2.FullAccountKeys
import build.wallet.cloud.backup.v2.FullAccountKeysMock
import com.github.michaelbull.result.Err
import com.github.michaelbull.result.Ok
import com.github.michaelbull.result.Result

class CloudBackupV3RestorerMock : CloudBackupV3Restorer {
  var result: Result<AccountRestoration, CloudBackupV3RestorerError> =
    Err(CloudBackupV3RestorerError.PkekMissingError)

  override suspend fun restore(cloudBackupV3: CloudBackupV3) = result

  override suspend fun restoreWithDecryptedKeys(
    cloudBackupV3: CloudBackupV3,
    keysInfo: FullAccountKeys,
  ) = result

  override suspend fun decryptCloudBackup(cloudBackupV3: CloudBackupV3) = Ok(FullAccountKeysMock)

  fun reset() {
    result = Err(CloudBackupV3RestorerError.PkekMissingError)
  }
}
