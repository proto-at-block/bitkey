package build.wallet.cloud.backup

import build.wallet.cloud.backup.FullAccountCloudBackupRestorer.AccountRestoration
import build.wallet.cloud.backup.v2.FullAccountKeys
import com.github.michaelbull.result.Err
import com.github.michaelbull.result.Ok
import com.github.michaelbull.result.Result

class FullAccountCloudBackupRestorerMock : FullAccountCloudBackupRestorer {
  var restoration: FullAccountCloudBackupRestorer.AccountRestoration? = AccountRestorationMock

  override suspend fun restoreFromBackup(
    cloudBackup: CloudBackup,
  ): Result<AccountRestoration, RestoreFromBackupError> {
    restoration?.let {
      return Ok(it)
    }

    return Err(RestoreFromBackupError.AccountBackupRestorationError())
  }

  override suspend fun restoreFromBackupWithDecryptedKeys(
    cloudBackup: CloudBackup,
    keysInfo: FullAccountKeys,
  ): Result<AccountRestoration, RestoreFromBackupError> {
    restoration?.let {
      return Ok(it)
    }

    return Err(RestoreFromBackupError.AccountBackupRestorationError())
  }
}
