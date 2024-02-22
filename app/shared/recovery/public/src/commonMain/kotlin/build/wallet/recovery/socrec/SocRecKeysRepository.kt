package build.wallet.recovery.socrec

import build.wallet.bitkey.keys.app.AppKey
import build.wallet.bitkey.socrec.SocRecKey
import com.github.michaelbull.result.Result
import com.github.michaelbull.result.coroutines.binding.binding
import com.github.michaelbull.result.mapError
import com.github.michaelbull.result.recoverIf
import kotlin.reflect.KClass

/**
 * A repository for SocRec keys that can generate keys if missing.
 */
class SocRecKeysRepository(
  private val socRecCrypto: SocRecCrypto,
  private val socRecKeysDao: SocRecKeysDao,
) {
  /**
   * Gets a socrec key of the given type, generating one if none is available.
   */
  suspend fun <T : SocRecKey> getOrCreateKey(
    keyFactory: (AppKey) -> T,
    keyClass: KClass<T>,
  ): Result<T, SocRecKeyError> =
    binding {
      socRecKeysDao.getKey(keyFactory, keyClass)
        .recoverIf({ it is SocRecKeyError.NoKeyAvailable }) {
          socRecCrypto.generateAsymmetricKey(keyFactory)
            .mapError { SocRecKeyError.UnableToGenerateKey(it) }
            .bind()
            .also { socRecKeysDao.saveKey(it) }
        }
        .bind()
    }

  suspend inline fun <reified T : SocRecKey> getOrCreateKey(
    noinline keyFactory: (AppKey) -> T,
  ): Result<T, SocRecKeyError> = getOrCreateKey(keyFactory, T::class)

  /**
   * Gets a socrec key of the given type, generating one if none is available. The returned key
   * will include private key material. If a key exists in the data store but private key does not,
   * [SocRecKeyError.NoPrivateKeyAvailable] will be returned.
   */
  suspend fun <T : SocRecKey> getKeyWithPrivateMaterialOrCreate(
    keyFactory: (AppKey) -> T,
    keyClass: KClass<T>,
  ): Result<T, SocRecKeyError> =
    binding {
      socRecKeysDao.getKeyWithPrivateMaterial(keyFactory, keyClass)
        .recoverIf({ it is SocRecKeyError.NoKeyAvailable }) {
          socRecCrypto.generateAsymmetricKey(keyFactory)
            .mapError { SocRecKeyError.UnableToGenerateKey(it) }
            .bind()
            .also { socRecKeysDao.saveKey(it) }
        }
        .bind()
    }

  suspend inline fun <reified T : SocRecKey> getKeyWithPrivateMaterialOrCreate(
    noinline keyFactory: (AppKey) -> T,
  ): Result<T, SocRecKeyError> = getKeyWithPrivateMaterialOrCreate(keyFactory, T::class)
}
