package build.wallet.sqldelight

import build.wallet.platform.random.UuidGenerator
import build.wallet.store.EncryptedKeyValueStoreFactory
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Serializes the read/possibly-generate/write flow in [loadDbKey].
 *
 * Multiple databases (e.g. `bitkey.db` and `bitkeyDebug.db`) are initialized on
 * independent coroutines and share the same `db-key`. Without this lock, two
 * concurrent first-time callers can both observe a `null` key, generate two
 * different keys, and each create their database with a key that is not the one
 * ultimately persisted. On the next app launch the orphaned database fails to
 * open with SQLCipher "file is not a database (code 26)" (SQLITE_NOTADB).
 */
private val dbKeyLock = Mutex()

@Suppress("ForbiddenMethodCall")
internal suspend fun loadDbKey(
  encryptedKeyValueStoreFactory: EncryptedKeyValueStoreFactory,
  databaseIntegrityChecker: DatabaseIntegrityChecker,
  uuidGenerator: UuidGenerator,
): String {
  val suspendSettings = encryptedKeyValueStoreFactory
    // Changing these values is a breaking change
    // These should only be changed with a migration plan otherwise data will be lost
    .getOrCreate(storeName = "SqlCipherStore")

  // The read/generate/write flow below must be atomic across concurrent callers,
  // otherwise different databases can be created with different keys while only
  // one key is persisted.
  return dbKeyLock.withLock {
    val databaseEncryptionKey = suspendSettings.getStringOrNull("db-key")

    // Ensure the database file and encryption keys are in an expected state.
    val isValid =
      databaseIntegrityChecker.purgeDatabaseStateIfInvalid(databaseEncryptionKey = databaseEncryptionKey)

    if (isValid && databaseEncryptionKey != null) {
      // If we already have a db key, we're guaranteed to be in a valid state, just return the
      // key
      databaseEncryptionKey
    } else {
      // Otherwise, create a new db key.
      val newKey = uuidGenerator.random()
      suspendSettings.putString("db-key", newKey)
      newKey
    }
  }
}

internal class DbNotEncryptedException(message: String?) : Exception(message)
