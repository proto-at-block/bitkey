package build.wallet.onboarding

import bitkey.serialization.hex.decodeHexWithResult
import build.wallet.cloud.backup.csek.SealedSsek
import build.wallet.di.AppScope
import build.wallet.di.BitkeyInject
import build.wallet.logging.logFailure
import build.wallet.store.EncryptedKeyValueStoreFactory
import build.wallet.store.clearWithResult
import build.wallet.store.getStringOrNullWithResult
import build.wallet.store.putStringWithResult
import com.github.michaelbull.result.Result
import com.github.michaelbull.result.coroutines.coroutineBinding

/**
 * Persists sealed SSEKs in a secure store, encoded as hex string.
 */
@BitkeyInject(AppScope::class)
class OnboardingKeyboxSealedSsekDaoImpl(
  private val encryptedKeyValueStoreFactory: EncryptedKeyValueStoreFactory,
) : OnboardingKeyboxSealedSsekDao {
  private suspend fun secureStore() =
    encryptedKeyValueStoreFactory.getOrCreate(storeName = STORE_NAME)

  override suspend fun get(): Result<SealedSsek?, Throwable> =
    coroutineBinding {
      secureStore()
        .getStringOrNullWithResult(key = KEY_SEALED_SSEK)
        .bind()
        ?.decodeHexWithResult()
        ?.bind()
    }.logFailure { "Failed to get $KEY_SEALED_SSEK from $STORE_NAME" }

  override suspend fun set(value: SealedSsek): Result<Unit, Throwable> {
    return secureStore()
      .putStringWithResult(key = KEY_SEALED_SSEK, value = value.hex())
      .logFailure { "Failed to set $KEY_SEALED_SSEK in $STORE_NAME" }
  }

  override suspend fun clear(): Result<Unit, Throwable> {
    return secureStore()
      .clearWithResult()
      .logFailure { "Failed to clear SealedSsekStore" }
  }

  private companion object {
    // Changing these values is a breaking change
    // These should only be changed with a migration plan
    const val STORE_NAME = "SealedSsekStore"
    const val KEY_SEALED_SSEK = "sealed-ssek-key"
  }
}
