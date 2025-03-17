package build.wallet.cloud.backup

import build.wallet.cloud.backup.CloudBackupV2Restorer.CloudBackupV2RestorerError
import build.wallet.cloud.backup.FullAccountCloudBackupRestorer.AccountRestoration
import build.wallet.cloud.backup.v2.FullAccountKeys
import com.github.michaelbull.result.Ok
import com.github.michaelbull.result.Result

class CloudBackupV2RestorerMock : CloudBackupV2Restorer {
  var result: Result<AccountRestoration, CloudBackupV2RestorerError> = Ok(AccountRestorationMock)

  override suspend fun restore(
    cloudBackupV2: CloudBackupV2,
  ): Result<AccountRestoration, CloudBackupV2RestorerError> {
    return result
  }

  override suspend fun restoreWithDecryptedKeys(
    cloudBackupV2: CloudBackupV2,
    keysInfo: FullAccountKeys,
  ) = result

  fun reset() {
    result = Ok(AccountRestorationMock)
  }
}
