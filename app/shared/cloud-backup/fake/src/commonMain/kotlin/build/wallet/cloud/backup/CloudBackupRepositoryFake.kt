package build.wallet.cloud.backup

import build.wallet.bitkey.f8e.AccountId
import build.wallet.cloud.store.CloudStoreAccount
import com.github.michaelbull.result.Err
import com.github.michaelbull.result.Ok
import com.github.michaelbull.result.Result
import io.kotest.matchers.maps.shouldBeEmpty

class CloudBackupRepositoryFake : CloudBackupRepository {
  var returnWriteError: CloudBackupError? = null
  var returnReadError: CloudBackupError? = null
  internal val backups = mutableMapOf<CloudStoreAccount, CloudBackup>()

  override suspend fun readBackup(
    cloudStoreAccount: CloudStoreAccount,
  ): Result<CloudBackup?, CloudBackupError> {
    returnReadError?.let { return Err(it) }

    return Ok(backups[cloudStoreAccount])
  }

  override suspend fun writeBackup(
    accountId: AccountId,
    cloudStoreAccount: CloudStoreAccount,
    backup: CloudBackup,
    requireAuthRefresh: Boolean,
  ): Result<Unit, CloudBackupError> {
    returnWriteError?.let { return Err(it) }

    backups[cloudStoreAccount] = backup
    return Ok(Unit)
  }

  override suspend fun clear(
    cloudStoreAccount: CloudStoreAccount,
    clearRemoteOnly: Boolean,
  ): Result<Unit, CloudBackupError> {
    returnWriteError?.let { return Err(it) }

    backups.clear()
    return Ok(Unit)
  }

  fun reset() {
    backups.clear()
    returnWriteError = null
    returnReadError = null
  }
}

fun CloudBackupRepositoryFake.shouldBeEmpty() = backups.shouldBeEmpty()
