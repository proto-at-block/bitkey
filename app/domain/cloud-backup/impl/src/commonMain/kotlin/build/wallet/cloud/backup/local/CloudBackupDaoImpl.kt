package build.wallet.cloud.backup.local

import bitkey.serialization.json.decodeFromStringResult
import bitkey.serialization.json.encodeToStringResult
import build.wallet.cloud.backup.CloudBackup
import build.wallet.cloud.backup.CloudBackupV2
import build.wallet.cloud.backup.CloudBackupV3
import build.wallet.di.AppScope
import build.wallet.di.BitkeyInject
import build.wallet.logging.logFailure
import build.wallet.store.EncryptedKeyValueStoreFactory
import build.wallet.store.clearWithResult
import build.wallet.store.getStringOrNullWithResult
import build.wallet.store.putStringWithResult
import com.github.michaelbull.result.Result
import com.github.michaelbull.result.coroutines.coroutineBinding
import com.github.michaelbull.result.flatMap
import com.github.michaelbull.result.mapError
import com.github.michaelbull.result.orElse
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
@BitkeyInject(AppScope::class)
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
      is CloudBackupV3 -> Json.encodeToStringResult(backup)
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
    coroutineBinding<CloudBackup?, BackupStorageError> {
      val backupJson =
        secureStore()
          .getStringOrNullWithResult(accountId)
          .logFailure { "Error reading local backup" }
          .mapError(::BackupStorageError)
          .bind()

      if (backupJson == null) return@coroutineBinding null

      // Try V3 first, then fall back to V2. See the cloud backup README.md.
      Json.decodeFromStringResult<CloudBackupV3>(backupJson)
        .orElse { Json.decodeFromStringResult<CloudBackupV2>(backupJson) }
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
