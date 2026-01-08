package build.wallet.cloud.backup

import app.cash.turbine.test
import build.wallet.bitkey.f8e.AccountId
import build.wallet.cloud.store.CloudStoreAccount
import build.wallet.coroutines.turbine.awaitNoEvents
import build.wallet.coroutines.turbine.awaitUntil
import build.wallet.testing.shouldBeErrOfType
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

  // Track the most recently written backup per cloud account as the "active" backup
  private val activeBackups = mutableMapOf<CloudStoreAccount, CloudBackup>()
  private val key = "cloud-backup"
  private var archiveKey = 1

  override suspend fun readActiveBackup(
    cloudStoreAccount: CloudStoreAccount,
  ): Result<CloudBackup?, CloudBackupError> {
    returnReadError?.let { return Err(it) }

    // Return the most recently written backup for this cloud account
    return Ok(activeBackups[cloudStoreAccount])
  }

  override suspend fun readAllBackups(
    cloudStoreAccount: CloudStoreAccount,
  ): Result<List<CloudBackup>, CloudBackupError> {
    returnReadError?.let { return Err(it) }

    return Ok(backups.value[cloudStoreAccount]?.values?.toList() ?: emptyList())
  }

  override suspend fun writeBackup(
    accountId: AccountId,
    cloudStoreAccount: CloudStoreAccount,
    backup: CloudBackup,
    requireAuthRefresh: Boolean,
  ): Result<Unit, CloudBackupError> {
    returnWriteError?.let { return Err(it) }

    // Set as the active backup for this cloud account
    activeBackups[cloudStoreAccount] = backup

    backups.update {
      it.toMutableMap().apply {
        // Add backup to existing map instead of replacing entire map
        // Use backup's accountId as key to allow multiple backups per cloud account
        val existingBackups = this[cloudStoreAccount] ?: emptyMap()
        this[cloudStoreAccount] = existingBackups.toMutableMap().apply {
          put(backup.accountId, backup)
        }
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
    accountId: AccountId?,
    cloudStoreAccount: CloudStoreAccount,
    clearRemoteOnly: Boolean,
  ): Result<Unit, CloudBackupError> {
    returnWriteError?.let { return Err(it) }

    activeBackups.remove(cloudStoreAccount)
    backups.update {
      it.toMutableMap().apply {
        this[cloudStoreAccount] = emptyMap()
      }
    }
    return Ok(Unit)
  }

  override suspend fun clearAll(
    cloudStoreAccount: CloudStoreAccount,
    clearRemoteOnly: Boolean,
  ): Result<Unit, CloudBackupError> {
    returnWriteError?.let { return Err(it) }
    activeBackups.clear()
    backups.value = emptyMap()
    return Ok(Unit)
  }

  override suspend fun readArchivedBackups(
    cloudStoreAccount: CloudStoreAccount,
  ): Result<List<CloudBackup>, CloudBackupError> {
    return Ok(backups.value[cloudStoreAccount]?.values?.toList() ?: emptyList())
  }

  override suspend fun migrateBackupToAccountIdKey(
    cloudStoreAccount: CloudStoreAccount,
  ): Result<Unit, CloudBackupError> {
    // For the fake implementation, we don't need to do actual migration
    // This is primarily used in tests where migration isn't the focus
    return Ok(Unit)
  }

  fun reset() {
    backups.value = emptyMap()
    activeBackups.clear()
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
  return readActiveBackup(cloudStoreAccount)
    .shouldBeOk().shouldNotBeNull()
}

suspend fun CloudBackupRepositoryFake.awaitAccountIdMismatchedBackup(
  cloudStoreAccount: CloudStoreAccount,
) {
  backups.test { awaitUntil { it[cloudStoreAccount] != null } }
  readActiveBackup(cloudStoreAccount)
    .shouldBeErrOfType<CloudBackupError.AccountIdMismatched>()
}
