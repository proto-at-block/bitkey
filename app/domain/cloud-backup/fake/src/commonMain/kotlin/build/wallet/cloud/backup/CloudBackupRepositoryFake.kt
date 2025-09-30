package build.wallet.cloud.backup

import app.cash.turbine.test
import build.wallet.bitkey.f8e.AccountId
import build.wallet.cloud.store.CloudStoreAccount
import build.wallet.coroutines.turbine.awaitNoEvents
import build.wallet.coroutines.turbine.awaitUntil
import build.wallet.testing.shouldBeOk
import com.github.michaelbull.result.Err
import com.github.michaelbull.result.Ok
import com.github.michaelbull.result.Result
import io.kotest.matchers.nulls.shouldNotBeNull
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update

class CloudBackupRepositoryFake : CloudBackupRepository {
  var returnWriteError: CloudBackupError? = null
  var returnReadError: CloudBackupError? = null
  internal val backups = MutableStateFlow<Map<CloudStoreAccount, Map<String, CloudBackup>>>(emptyMap())
  private val key = "cloud-backup"
  private var archiveKey = 1

  override suspend fun readActiveBackup(
    cloudStoreAccount: CloudStoreAccount,
  ): Result<CloudBackup?, CloudBackupError> {
    returnReadError?.let { return Err(it) }

    return Ok(backups.value[cloudStoreAccount]?.get(key))
  }

  override suspend fun writeBackup(
    accountId: AccountId,
    cloudStoreAccount: CloudStoreAccount,
    backup: CloudBackup,
    requireAuthRefresh: Boolean,
  ): Result<Unit, CloudBackupError> {
    returnWriteError?.let { return Err(it) }

    backups.update {
      it.toMutableMap().apply {
        this[cloudStoreAccount] = mapOf(key to backup)
      }
    }
    return Ok(Unit)
  }

  override suspend fun archiveBackup(
    cloudStoreAccount: CloudStoreAccount,
    backup: CloudBackup,
  ): Result<Unit, CloudBackupError> {
    backups.update {
      it.toMutableMap().apply {
        this[cloudStoreAccount] = (this[cloudStoreAccount] ?: emptyMap()).toMutableMap().apply {
          put("$key-$archiveKey", backup)
        }
        archiveKey++
      }
    }
    return Ok(Unit)
  }

  override suspend fun clear(
    cloudStoreAccount: CloudStoreAccount,
    clearRemoteOnly: Boolean,
  ): Result<Unit, CloudBackupError> {
    returnWriteError?.let { return Err(it) }

    backups.value = emptyMap()
    return Ok(Unit)
  }

  override suspend fun readArchivedBackups(
    cloudStoreAccount: CloudStoreAccount,
  ): Result<List<CloudBackup>, CloudBackupError> {
    return Ok(backups.value[cloudStoreAccount]?.values?.toList() ?: emptyList())
  }

  fun reset() {
    backups.value = emptyMap()
    returnWriteError = null
    returnReadError = null
    archiveKey = 1
  }
}

suspend fun CloudBackupRepositoryFake.awaitNoBackups() {
  backups.test {
    awaitUntil { it.isEmpty() }
    // No new backups should be added.
    awaitNoEvents()
  }
}

suspend fun CloudBackupRepositoryFake.awaitBackup(
  cloudStoreAccount: CloudStoreAccount,
): CloudBackup {
  backups.test { awaitUntil { it[cloudStoreAccount] != null } }
  return readActiveBackup(cloudStoreAccount).shouldBeOk().shouldNotBeNull()
}
