package build.wallet.cloud.backup

import build.wallet.cloud.backup.FullAccountCloudBackupRestorer.AccountRestoration
import build.wallet.cloud.backup.v2.FullAccountKeys
import com.github.michaelbull.result.Ok
import com.github.michaelbull.result.Result

class FullAccountCloudBackupRestorerMock : FullAccountCloudBackupRestorer {
  var restoration = AccountRestorationMock

  override suspend fun restoreFromBackup(
    cloudBackup: CloudBackup,
  ): Result<AccountRestoration, RestoreFromBackupError> = Ok(restoration)

  override suspend fun restoreFromBackupWithDecryptedKeys(
    cloudBackup: CloudBackup,
    keysInfo: FullAccountKeys,
  ): Result<AccountRestoration, RestoreFromBackupError> = Ok(restoration)
}
