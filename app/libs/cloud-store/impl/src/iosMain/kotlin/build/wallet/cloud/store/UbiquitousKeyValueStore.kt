package build.wallet.cloud.store

import build.wallet.catchingResult
import build.wallet.di.AppScope
import build.wallet.di.BitkeyInject
import build.wallet.logging.logDebug
import build.wallet.logging.logError
import build.wallet.logging.logWarn
import com.github.michaelbull.result.Result
import com.github.michaelbull.result.fold
import com.github.michaelbull.result.mapError
import kotlinx.datetime.Clock
import platform.Foundation.NSNotificationCenter
import platform.Foundation.NSUbiquitousKeyValueStore
import platform.Foundation.NSUbiquitousKeyValueStoreAccountChange
import platform.Foundation.NSUbiquitousKeyValueStoreChangeReasonKey
import platform.Foundation.NSUbiquitousKeyValueStoreChangedKeysKey
import platform.Foundation.NSUbiquitousKeyValueStoreDidChangeExternallyNotification
import platform.Foundation.NSUbiquitousKeyValueStoreInitialSyncChange
import platform.Foundation.NSUbiquitousKeyValueStoreQuotaViolationChange
import platform.Foundation.NSUbiquitousKeyValueStoreServerChange

@Suppress("unused", "ClassName")
@BitkeyInject(AppScope::class)
class UbiquitousKeyValueStoreImpl(
  private val clock: Clock,
) : UbiquitousKeyValueStore {
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
      val userInfo = notification?.userInfo
      val reason = userInfo?.get(NSUbiquitousKeyValueStoreChangeReasonKey) as? Long
      val changedKeys = userInfo?.get(NSUbiquitousKeyValueStoreChangedKeysKey) as? List<*>
      val keysDescription = changedKeys?.joinToString() ?: "unknown"

      when (reason) {
        NSUbiquitousKeyValueStoreServerChange ->
          logDebug { "iCloud KVS: sync from server, changedKeys=[$keysDescription]" }
        NSUbiquitousKeyValueStoreInitialSyncChange ->
          logDebug { "iCloud KVS: initial sync completed, changedKeys=[$keysDescription]" }
        NSUbiquitousKeyValueStoreQuotaViolationChange ->
          logError { "iCloud KVS: quota exceeded, changedKeys=[$keysDescription]" }
        NSUbiquitousKeyValueStoreAccountChange ->
          logWarn { "iCloud KVS: account changed, changedKeys=[$keysDescription]" }
        else ->
          logWarn { "iCloud KVS: external change (reason=$reason), changedKeys=[$keysDescription]" }
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
  ): Result<Unit, UbiquitousKeyValueStoreError> {
    return catchingResult {
      iCloudKeyValueStore.setString(
        forKey = key,
        aString = value
      )
    }
      .mapError {
        UbiquitousKeyValueStoreError(
          message = it.message ?: it.toString(),
          cause = it
        )
      }
      .also { result ->
        result.fold(
          success = {
            logDebug { "iCloud KVS: successfully wrote value for key=$key (size=${value.length} chars)" }
          },
          failure = { error ->
            logError(throwable = error.cause ?: error) {
              "iCloud KVS: error writing key=$key: $error"
            }
          }
        )
        requestSync()
      }
  }

  override fun getString(
    account: iCloudAccount,
    key: String,
  ): Result<String?, UbiquitousKeyValueStoreError> {
    requestSync()

    return catchingResult { iCloudKeyValueStore.stringForKey(key) }
      .mapError {
        UbiquitousKeyValueStoreError(
          message = it.message ?: it.toString(),
          cause = it
        )
      }
      .also { result ->
        result.fold(
          success = { value ->
            if (value == null) {
              logDebug { "iCloud KVS: no value found for key=$key" }
            } else {
              logDebug { "iCloud KVS: successfully read value for key=$key (size=${value.length} chars)" }
            }
          },
          failure = { error ->
            logError(throwable = error.cause ?: error) {
              "iCloud KVS: error reading key=$key: $error"
            }
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
      .mapError {
        UbiquitousKeyValueStoreError(
          message = it.message ?: it.toString(),
          cause = it
        )
      }
      .also { result ->
        result.fold(
          success = {
            logDebug { "iCloud KVS: removed value for key=$key" }
            requestSync()
          },
          failure = { error ->
            logError(throwable = error.cause ?: error) {
              "iCloud KVS: error removing key=$key: $error"
            }
          }
        )
      }
  }

  override fun keys(account: CloudStoreAccount): Result<List<String>, CloudError> {
    return catchingResult {
      iCloudKeyValueStore.dictionaryRepresentation.keys.map { it as String }
    }
      .mapError {
        UbiquitousKeyValueStoreError(
          message = it.message ?: it.toString(),
          cause = it
        )
      }
      .also { result ->
        result.fold(
          success = { keys ->
            logDebug { "iCloud KVS: listed ${keys.size} keys" }
          },
          failure = { error ->
            logError(throwable = error.cause ?: error) {
              "iCloud KVS: error listing keys: $error"
            }
          }
        )
      }
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
