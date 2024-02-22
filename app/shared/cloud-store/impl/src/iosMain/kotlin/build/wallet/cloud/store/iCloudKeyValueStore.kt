package build.wallet.cloud.store

import build.wallet.catching
import com.github.michaelbull.result.Result
import com.github.michaelbull.result.mapError
import kotlinx.datetime.Clock
import platform.Foundation.NSUbiquitousKeyValueStore

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
}

@Suppress("unused", "ClassName")
class iCloudKeyValueStoreImpl(
  private val clock: Clock,
) : iCloudKeyValueStore {
  init {
    // Initial sync.
    requestSync()
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
    return Result
      .catching {
        iCloudKeyValueStore.setString(
          forKey = key,
          aString = value
        )
      }
      .mapError { iCloudKeyValueStoreError(message = it.toString()) }
      .also { requestSync() }
  }

  override fun getString(
    account: iCloudAccount,
    key: String,
  ): Result<String?, iCloudKeyValueStoreError> {
    requestSync()

    return Result
      .catching { iCloudKeyValueStore.stringForKey(key) }
      .mapError { iCloudKeyValueStoreError(message = it.toString()) }
  }

  override fun removeString(
    account: CloudStoreAccount,
    key: String,
  ): Result<Unit, CloudError> {
    return Result
      .catching {
        iCloudKeyValueStore.removeObjectForKey(key)
        // Remove dummy value that was added in the init
        iCloudKeyValueStore.removeObjectForKey("fake")
      }
      .mapError { iCloudKeyValueStoreError(message = it.toString()) }
      .also { requestSync() }
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
