package build.wallet.cloud.backup.local

import build.wallet.cloud.backup.CloudBackup
import build.wallet.cloud.backup.CloudBackupV2
import build.wallet.logging.logFailure
import build.wallet.serialization.json.decodeFromStringResult
import build.wallet.serialization.json.encodeToStringResult
import build.wallet.store.EncryptedKeyValueStoreFactory
import build.wallet.store.clearWithResult
import build.wallet.store.getStringOrNullWithResult
import build.wallet.store.putStringWithResult
import com.github.michaelbull.result.Result
import com.github.michaelbull.result.coroutines.binding.binding
import com.github.michaelbull.result.flatMap
import com.github.michaelbull.result.mapError
import com.russhwolf.settings.coroutines.SuspendSettings
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.serialization.json.Json

/**
 * Implementation of [CloudBackupDao] which encodes/decodes encrypted [CloudBackup] as JSON
 * and persists it locally using [EncryptedKeyValueStoreFactory].
 */
class CloudBackupDaoImpl(
  private val encryptedKeyValueStoreFactory: EncryptedKeyValueStoreFactory,
) : CloudBackupDao {
  private suspend fun secureStore(): SuspendSettings =
    encryptedKeyValueStoreFactory.getOrCreate(storeName = "BackupStore")

  private val backupFlow = MutableSharedFlow<CloudBackup?>()

  override suspend fun set(
    accountId: String,
    backup: CloudBackup,
  ): Result<Unit, BackupStorageError> {
    return when (backup) {
      is CloudBackupV2 -> Json.encodeToStringResult(backup)
    }
      .mapError { error -> BackupStorageError(error) }
      .flatMap { backupJson ->
        backupFlow.emit(backup)
        secureStore()
          .putStringWithResult(key = accountId, value = backupJson)
          .mapError { BackupStorageError(it) }
      }
      .logFailure { "Error writing backup locally." }
  }

  override suspend fun get(accountId: String): Result<CloudBackup?, BackupStorageError> =
    binding {
      val backupJson =
        secureStore()
          .getStringOrNullWithResult(accountId)
          .logFailure { "Error reading local backup" }
          .mapError(::BackupStorageError)
          .bind()

      if (backupJson == null) return@binding null

      Json.decodeFromStringResult<CloudBackupV2>(backupJson)
        .mapError(::BackupStorageError)
        .bind()
    }

  override suspend fun clear(): Result<Unit, Throwable> {
    backupFlow.emit(null)
    return secureStore().clearWithResult()
  }

  override fun backup(accountId: String): Flow<CloudBackup?> {
    return flow {
      emit(get(accountId).component1())
      emitAll(backupFlow)
    }
  }
}
