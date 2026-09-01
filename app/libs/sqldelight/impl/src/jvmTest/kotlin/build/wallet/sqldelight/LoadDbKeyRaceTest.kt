package build.wallet.sqldelight

import build.wallet.platform.random.UuidGenerator
import build.wallet.store.EncryptedKeyValueStoreFactory
import com.russhwolf.settings.coroutines.SuspendSettings
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Regression test for the shared `db-key` initialization race.
 *
 * Production scenario (Android): [BitkeyDatabaseProviderImpl] initializes
 * `bitkey.db` and `bitkeyDebug.db` on two independent Dispatchers.IO coroutines
 * during a fresh install. Both go through [loadDbKey]'s read/possibly-generate/write
 * flow against the same `SqlCipherStore`/`db-key` entry.
 *
 * Before the fix (no mutex around the read/generate/write), two concurrent
 * first-time callers could both observe a `null` key, generate two *different*
 * keys, and each create their database with a key that is not the one ultimately
 * persisted. On the next launch the orphaned database fails to open with
 * SQLCipher "file is not a database (code 26)" (SQLITE_NOTADB).
 *
 * This test widens the read→write window by suspending inside the first-time
 * (null) read path, and asserts that concurrent callers still receive the same,
 * single persisted key. Without the mutex in [loadDbKey], this test fails with
 * two distinct keys being handed out.
 */
class LoadDbKeyRaceTest : FunSpec({

  test("concurrent first-time loadDbKey callers receive the same persisted key") {
    val backingStore = InMemorySuspendSettings()

    // Widen the race window: any caller that observes a null key suspends here,
    // giving the other coroutine a chance to enter the read path concurrently
    // (which the mutex in loadDbKey must prevent). The gate releases either when
    // a second reader arrives inside the window (unfixed behavior) or after a
    // short timeout (fixed behavior, where the mutex serializes callers).
    val secondReaderArrived = CompletableDeferred<Unit>()
    var nullReads = 0
    val readerLock = Any()

    val gatedSettings = object : SuspendSettings by backingStore {
      override suspend fun getStringOrNull(key: String): String? {
        val value = backingStore.getStringOrNull(key)
        if (value == null) {
          synchronized(readerLock) {
            nullReads += 1
            if (nullReads == 2) secondReaderArrived.complete(Unit)
          }
          // Hold the first-time reader in the window. If loadDbKey is properly
          // serialized, no second reader can arrive and we proceed after the
          // timeout with the race window proven closed.
          withTimeoutOrNull(500) { secondReaderArrived.await() }
        }
        return value
      }
    }

    val storeFactory = object : EncryptedKeyValueStoreFactory {
      override suspend fun getOrCreate(storeName: String): SuspendSettings = gatedSettings
    }

    val integrityChecker = object : DatabaseIntegrityChecker {
      override suspend fun purgeDatabaseStateIfInvalid(databaseEncryptionKey: String?) = true
    }

    var uuidCounter = 0
    val uuidLock = Any()
    val uuidGenerator = UuidGenerator { synchronized(uuidLock) { "uuid-${uuidCounter++}" } }

    // Simulate bitkey.db and bitkeyDebug.db initializing concurrently on fresh install.
    val (keyForBitkeyDb, keyForDebugDb) = coroutineScope {
      val a = async { loadDbKey(storeFactory, integrityChecker, uuidGenerator) }
      val b = async { loadDbKey(storeFactory, integrityChecker, uuidGenerator) }
      a.await() to b.await()
    }

    // Both databases must be keyed with the same, single generated key...
    keyForBitkeyDb shouldBe keyForDebugDb
    uuidCounter shouldBe 1
    // ...and that key must be the one persisted for the next launch.
    backingStore.getStringOrNull("db-key") shouldBe keyForBitkeyDb
    // The mutex must have prevented a second caller from entering the null-read window.
    nullReads shouldBe 1
  }

  test("sequential callers receive the same persisted key (control)") {
    val backingStore = InMemorySuspendSettings()
    val storeFactory = object : EncryptedKeyValueStoreFactory {
      override suspend fun getOrCreate(storeName: String): SuspendSettings = backingStore
    }
    val integrityChecker = object : DatabaseIntegrityChecker {
      override suspend fun purgeDatabaseStateIfInvalid(databaseEncryptionKey: String?) = true
    }
    var uuidCounter = 0
    val uuidGenerator = UuidGenerator { "uuid-${uuidCounter++}" }

    val first = loadDbKey(storeFactory, integrityChecker, uuidGenerator)
    val second = loadDbKey(storeFactory, integrityChecker, uuidGenerator)

    first shouldBe second
    backingStore.getStringOrNull("db-key") shouldBe first
  }
})

/**
 * Minimal in-memory [SuspendSettings] backed by a synchronized map.
 */
private class InMemorySuspendSettings : SuspendSettings {
  private val map = mutableMapOf<String, Any>()
  private val lock = Any()

  override suspend fun keys(): Set<String> = synchronized(lock) { map.keys.toSet() }

  override suspend fun size(): Int = synchronized(lock) { map.size }

  override suspend fun clear() {
    synchronized(lock) { map.clear() }
  }

  override suspend fun remove(key: String) {
    synchronized(lock) { map.remove(key) }
  }

  override suspend fun hasKey(key: String): Boolean = synchronized(lock) { map.containsKey(key) }

  override suspend fun putInt(key: String, value: Int) {
    synchronized(lock) { map[key] = value }
  }

  override suspend fun getInt(key: String, defaultValue: Int): Int =
    getIntOrNull(key) ?: defaultValue

  override suspend fun getIntOrNull(key: String): Int? = synchronized(lock) { map[key] as? Int }

  override suspend fun putLong(key: String, value: Long) {
    synchronized(lock) { map[key] = value }
  }

  override suspend fun getLong(key: String, defaultValue: Long): Long =
    getLongOrNull(key) ?: defaultValue

  override suspend fun getLongOrNull(key: String): Long? = synchronized(lock) { map[key] as? Long }

  override suspend fun putString(key: String, value: String) {
    synchronized(lock) { map[key] = value }
  }

  override suspend fun getString(key: String, defaultValue: String): String =
    getStringOrNull(key) ?: defaultValue

  override suspend fun getStringOrNull(key: String): String? =
    synchronized(lock) { map[key] as? String }

  override suspend fun putFloat(key: String, value: Float) {
    synchronized(lock) { map[key] = value }
  }

  override suspend fun getFloat(key: String, defaultValue: Float): Float =
    getFloatOrNull(key) ?: defaultValue

  override suspend fun getFloatOrNull(key: String): Float? =
    synchronized(lock) { map[key] as? Float }

  override suspend fun putDouble(key: String, value: Double) {
    synchronized(lock) { map[key] = value }
  }

  override suspend fun getDouble(key: String, defaultValue: Double): Double =
    getDoubleOrNull(key) ?: defaultValue

  override suspend fun getDoubleOrNull(key: String): Double? =
    synchronized(lock) { map[key] as? Double }

  override suspend fun putBoolean(key: String, value: Boolean) {
    synchronized(lock) { map[key] = value }
  }

  override suspend fun getBoolean(key: String, defaultValue: Boolean): Boolean =
    getBooleanOrNull(key) ?: defaultValue

  override suspend fun getBooleanOrNull(key: String): Boolean? =
    synchronized(lock) { map[key] as? Boolean }
}
