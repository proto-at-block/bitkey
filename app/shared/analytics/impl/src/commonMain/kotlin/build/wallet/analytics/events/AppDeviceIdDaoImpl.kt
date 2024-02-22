@file:OptIn(ExperimentalSettingsApi::class)

package build.wallet.analytics.events

import build.wallet.logging.logFailure
import build.wallet.platform.random.Uuid
import build.wallet.store.EncryptedKeyValueStoreFactory
import build.wallet.store.getStringOrNullWithResult
import build.wallet.store.putStringWithResult
import com.github.michaelbull.result.Result
import com.github.michaelbull.result.coroutines.binding.binding
import com.russhwolf.settings.ExperimentalSettingsApi

/**
 * A dao for storing and retrieving the AppDeviceID.
 */
class AppDeviceIdDaoImpl(
  private val encryptedKeyValueStoreFactory: EncryptedKeyValueStoreFactory,
  uuid: Uuid,
) : AppDeviceIdDao {
  private suspend fun secureStore() = encryptedKeyValueStoreFactory.getOrCreate(STORE_NAME)

  private val uuid = uuid

  override suspend fun getOrCreateAppDeviceIdIfNotExists(): Result<String, Throwable> {
    val secureStore = secureStore()
    return binding {
      val appDeviceId = secureStore.getStringOrNullWithResult(KEY).bind()

      if (appDeviceId != null) {
        appDeviceId
      } else {
        val newAppDeviceId = uuid.random()
        secureStore.putStringWithResult(key = KEY, value = newAppDeviceId).bind()
        newAppDeviceId
      }
    }.logFailure { "Failed to get App Device ID" }
  }

  private companion object {
    const val STORE_NAME = "AppDeviceIdStore"
    const val KEY = "app-device-id"
  }
}
