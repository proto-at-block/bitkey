package build.wallet.cloud.backup.local

import build.wallet.cloud.backup.CloudBackup
import com.github.michaelbull.result.Err
import com.github.michaelbull.result.Ok
import com.github.michaelbull.result.Result
import io.kotest.matchers.nulls.shouldBeNull
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

class CloudBackupDaoFake : CloudBackupDao {
  internal val backups = mutableMapOf<String, MutableStateFlow<CloudBackup?>>()
  var returnError = false

  override suspend fun set(
    accountId: String,
    backup: CloudBackup,
  ): Result<Unit, BackupStorageError> {
    if (returnError) return Err(BackupStorageError())

    backups.getOrPut(accountId) { MutableStateFlow(null) }.value = backup
    return Ok(Unit)
  }

  override suspend fun get(accountId: String): Result<CloudBackup?, BackupStorageError> {
    if (returnError) return Err(BackupStorageError())

    return Ok(backups.getOrPut(accountId) { MutableStateFlow(null) }.value)
  }

  override suspend fun clear(): Result<Unit, Throwable> {
    if (returnError) return Err(BackupStorageError())

    backups.clear()
    return Ok(Unit)
  }

  override fun backup(accountId: String): Flow<CloudBackup?> {
    return backups.getOrPut(accountId) { MutableStateFlow(null) }
  }

  fun reset() {
    backups.clear()
    returnError = false
  }

  fun shouldBeEmpty() = backups.values.forEach { it.value.shouldBeNull() }
}
