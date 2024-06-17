package build.wallet.onboarding

import build.wallet.cloud.backup.csek.SealedCsek
import build.wallet.logging.log
import build.wallet.logging.logFailure
import build.wallet.serialization.hex.decodeHexWithResult
import build.wallet.store.EncryptedKeyValueStoreFactory
import build.wallet.store.clearWithResult
import build.wallet.store.getStringOrNullWithResult
import build.wallet.store.putStringWithResult
import com.github.michaelbull.result.Result
import com.github.michaelbull.result.coroutines.coroutineBinding
import okio.ByteString

/**
 * Persists sealed CSEKs in a secure store, encoded as hex string.
 */
class OnboardingKeyboxSealedCsekDaoImpl(
  private val encryptedKeyValueStoreFactory: EncryptedKeyValueStoreFactory,
) : OnboardingKeyboxSealedCsekDao {
  private suspend fun secureStore() =
    encryptedKeyValueStoreFactory.getOrCreate(storeName = STORE_NAME)

  override suspend fun get(): Result<ByteString?, Throwable> =
    coroutineBinding {
      log { "Fetching sealed CSEK" }

      secureStore()
        .getStringOrNullWithResult(key = KEY_SEALED_CSEK)
        .bind()
        ?.decodeHexWithResult()
        ?.bind()
    }.logFailure { "Failed to get $KEY_SEALED_CSEK from $STORE_NAME" }

  override suspend fun set(value: SealedCsek): Result<Unit, Throwable> {
    log { "Setting sealed CSEK" }

    return secureStore()
      .putStringWithResult(key = KEY_SEALED_CSEK, value = value.hex())
      .logFailure { "Failed to set $KEY_SEALED_CSEK in $STORE_NAME" }
  }

  override suspend fun clear(): Result<Unit, Throwable> {
    log { "Clearing sealed CSEK" }

    return secureStore()
      .clearWithResult()
      .logFailure { "Failed to clear SealedCsekStore" }
  }

  private companion object {
    // Changing these values is a breaking change
    // These should only be changed with a migration plan
    const val STORE_NAME = "SealedCsekStore"
    const val KEY_SEALED_CSEK = "sealed-csek-key"
  }
}
