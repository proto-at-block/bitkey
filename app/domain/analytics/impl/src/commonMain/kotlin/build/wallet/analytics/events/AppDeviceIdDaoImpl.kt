package build.wallet.analytics.events

import build.wallet.di.AppScope
import build.wallet.di.BitkeyInject
import build.wallet.logging.logFailure
import build.wallet.platform.random.UuidGenerator
import build.wallet.store.EncryptedKeyValueStoreFactory
import build.wallet.store.getStringOrNullWithResult
import build.wallet.store.putStringWithResult
import com.github.michaelbull.result.Result
import com.github.michaelbull.result.coroutines.coroutineBinding

/**
 * A dao for storing and retrieving the AppDeviceID.
 */

@BitkeyInject(AppScope::class)
class AppDeviceIdDaoImpl(
  private val encryptedKeyValueStoreFactory: EncryptedKeyValueStoreFactory,
  private val uuidGenerator: UuidGenerator,
) : AppDeviceIdDao {
  private suspend fun secureStore() = encryptedKeyValueStoreFactory.getOrCreate(STORE_NAME)

  override suspend fun getOrCreateAppDeviceIdIfNotExists(): Result<String, Throwable> {
    val secureStore = secureStore()
    return coroutineBinding {
      val appDeviceId = secureStore.getStringOrNullWithResult(KEY).bind()

      if (appDeviceId != null) {
        appDeviceId
      } else {
        val newAppDeviceId = uuidGenerator.random()
        secureStore.putStringWithResult(key = KEY, value = newAppDeviceId).bind()
        newAppDeviceId
      }
    }.logFailure { "Failed to get App Device ID" }
  }

  private companion object {
    // Changing these values is a breaking change
    // These should only be changed with a migration plan otherwise data will be lost or app could crash
    const val STORE_NAME = "AppDeviceIdStore"
    const val KEY = "app-device-id-key"
  }
}
