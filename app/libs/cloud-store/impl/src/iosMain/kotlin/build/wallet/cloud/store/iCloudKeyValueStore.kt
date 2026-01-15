package build.wallet.cloud.store

import build.wallet.catchingResult
import build.wallet.di.AppScope
import build.wallet.di.BitkeyInject
import build.wallet.logging.logError
import build.wallet.logging.logInfo
import build.wallet.logging.logWarn
import com.github.michaelbull.result.Result
import com.github.michaelbull.result.fold
import com.github.michaelbull.result.mapError
import kotlinx.datetime.Clock
import platform.Foundation.NSNotificationCenter
import platform.Foundation.NSUbiquitousKeyValueStore
import platform.Foundation.NSUbiquitousKeyValueStoreAccountChange
import platform.Foundation.NSUbiquitousKeyValueStoreChangeReasonKey
import platform.Foundation.NSUbiquitousKeyValueStoreDidChangeExternallyNotification
import platform.Foundation.NSUbiquitousKeyValueStoreInitialSyncChange
import platform.Foundation.NSUbiquitousKeyValueStoreQuotaViolationChange
import platform.Foundation.NSUbiquitousKeyValueStoreServerChange

@Suppress("ClassName")
interface iCloudKeyValueStore {
  fun setString(
    account: iCloudAccount,
    key: String,
    value: String,
  ): Result<Unit, iCloudKeyValueStoreError>

  fun getString(
    account: iCloudAccount,
    key: String,
  ): Result<String?, iCloudKeyValueStoreError>

  fun removeString(
    account: CloudStoreAccount,
    key: String,
  ): Result<Unit, CloudError>

  fun keys(account: CloudStoreAccount): Result<List<String>, CloudError>
}

@Suppress("unused", "ClassName")
@BitkeyInject(AppScope::class)
class iCloudKeyValueStoreImpl(
  private val clock: Clock,
) : iCloudKeyValueStore {
  init {
    // Initial sync.
    requestSync()
    // Observe iCloud KVS change notifications
    observeICloudChanges()
  }

  private fun observeICloudChanges() {
    NSNotificationCenter.defaultCenter.addObserverForName(
      name = NSUbiquitousKeyValueStoreDidChangeExternallyNotification,
      `object` = null,
      queue = null
    ) { notification ->
      val reason = notification?.userInfo?.get(NSUbiquitousKeyValueStoreChangeReasonKey) as? Long
      when (reason) {
        NSUbiquitousKeyValueStoreServerChange -> logInfo { "iCloud KVS sync from server" }
        NSUbiquitousKeyValueStoreInitialSyncChange -> logInfo { "iCloud KVS initial sync" }
        NSUbiquitousKeyValueStoreQuotaViolationChange -> logError { "iCloud KVS quota exceeded" }
        NSUbiquitousKeyValueStoreAccountChange -> logWarn { "iCloud KVS account changed" }
        else -> logWarn { "iCloud KVS change reason=$reason" }
      }
    }
  }

  /**
   * The shared iCloud key-value store object.
   * This store is tied to the unique identifier string your app provides in its entitlement requests.
   * See https://developer.apple.com/documentation/foundation/nsubiquitouskeyvaluestore/1413949-defaultstore/
   */
  private val iCloudKeyValueStore: NSUbiquitousKeyValueStore
    get() = NSUbiquitousKeyValueStore.defaultStore

  override fun setString(
    account: iCloudAccount,
    key: String,
    value: String,
  ): Result<Unit, iCloudKeyValueStoreError> {
    return catchingResult {
      iCloudKeyValueStore.setString(
        forKey = key,
        aString = value
      )
    }
      .mapError { iCloudKeyValueStoreError(message = it.toString()) }
      .also { result ->
        result.fold(
          success = {
            logInfo { "iCloud KVS: successfully wrote value for key=$key (size=${value.length} chars)" }
          },
          failure = { error ->
            logError { "iCloud KVS: error writing key=$key: $error" }
          }
        )
        requestSync()
      }
  }

  override fun getString(
    account: iCloudAccount,
    key: String,
  ): Result<String?, iCloudKeyValueStoreError> {
    requestSync()

    return catchingResult { iCloudKeyValueStore.stringForKey(key) }
      .mapError { iCloudKeyValueStoreError(message = it.toString()) }
      .also { result ->
        result.fold(
          success = { value ->
            if (value == null) {
              logInfo { "iCloud KVS: no value found for key=$key" }
            } else {
              logInfo { "iCloud KVS: successfully read value for key=$key (size=${value.length} chars)" }
            }
          },
          failure = { error ->
            logError { "iCloud KVS: error reading key=$key: $error" }
          }
        )
      }
  }

  override fun removeString(
    account: CloudStoreAccount,
    key: String,
  ): Result<Unit, CloudError> {
    return catchingResult {
      iCloudKeyValueStore.removeObjectForKey(key)
      // Remove dummy value that was added in the init
      iCloudKeyValueStore.removeObjectForKey("fake")
    }
      .mapError { iCloudKeyValueStoreError(message = it.toString()) }
      .also { requestSync() }
  }

  override fun keys(account: CloudStoreAccount): Result<List<String>, CloudError> {
    return catchingResult {
      iCloudKeyValueStore.dictionaryRepresentation.keys.map { it as String }
    }.mapError { iCloudKeyValueStoreError(message = it.toString()) }
  }

  /**
   * Writing to iCloud ubiquity key-value store is asynchronous, it does not guarantee that the data
   * is written immediately. This method writes sync timestamp value to store (to guarantee change
   * in the store) and requests synchronization to request sync as soon as possible. This should be
   * used before reading and after writing to the store.
   */
  private fun requestSync() {
    val syncTime = clock.now().toString()
    iCloudKeyValueStore.setString(aString = syncTime, forKey = "sync")
    iCloudKeyValueStore.synchronize()
  }
}
