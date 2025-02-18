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
  internal val backups = MutableStateFlow<Map<CloudStoreAccount, CloudBackup>>(emptyMap())

  override suspend fun readBackup(
    cloudStoreAccount: CloudStoreAccount,
  ): Result<CloudBackup?, CloudBackupError> {
    returnReadError?.let { return Err(it) }

    return Ok(backups.value[cloudStoreAccount])
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
        this[cloudStoreAccount] = backup
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

  fun reset() {
    backups.value = emptyMap()
    returnWriteError = null
    returnReadError = null
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
  return readBackup(cloudStoreAccount).shouldBeOk().shouldNotBeNull()
}
