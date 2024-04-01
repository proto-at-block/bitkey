package build.wallet.recovery.socrec

import build.wallet.bitkey.keys.app.AppKey
import build.wallet.bitkey.socrec.SocRecKey
import build.wallet.crypto.CurveType
import build.wallet.crypto.PublicKey
import com.github.michaelbull.result.Result
import com.github.michaelbull.result.coroutines.binding.binding
import com.github.michaelbull.result.mapError
import com.github.michaelbull.result.recoverIf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
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
  suspend fun <T> getOrCreateKey(
    keyClass: KClass<T>,
  ): Result<PublicKey<T>, SocRecKeyError> where T : SocRecKey, T : CurveType.Curve25519 =
    binding {
      socRecKeysDao.getPublicKey(keyClass)
        .recoverIf({ it is SocRecKeyError.NoKeyAvailable }) {
          withContext(Dispatchers.Default) {
            socRecCrypto.generateAsymmetricKey<T>()
              .mapError { SocRecKeyError.UnableToGenerateKey(it) }
              .bind()
              .also { socRecKeysDao.saveKey(it, keyClass) }
              .publicKey
          }
        }
        .bind()
    }

  suspend inline fun <reified T> getOrCreateKey(): Result<PublicKey<T>, SocRecKeyError> where T : SocRecKey, T : CurveType.Curve25519 =
    getOrCreateKey(T::class)

  /**
   * Gets a socrec key of the given type, generating one if none is available. The returned key
   * will include private key material. If a key exists in the data store but private key does not,
   * [SocRecKeyError.NoPrivateKeyAvailable] will be returned.
   */
  suspend fun <T> getKeyWithPrivateMaterialOrCreate(
    keyClass: KClass<T>,
  ): Result<AppKey<T>, SocRecKeyError> where T : SocRecKey, T : CurveType.Curve25519 =
    binding {
      socRecKeysDao.getKeyWithPrivateMaterial(keyClass)
        .recoverIf({ it is SocRecKeyError.NoKeyAvailable }) {
          withContext(Dispatchers.Default) {
            socRecCrypto.generateAsymmetricKey<T>()
              .mapError { SocRecKeyError.UnableToGenerateKey(it) }
              .bind()
              .also { socRecKeysDao.saveKey(it, keyClass) }
          }
        }
        .bind()
    }

  suspend inline fun <reified T> getKeyWithPrivateMaterialOrCreate(): Result<AppKey<T>, SocRecKeyError> where T : SocRecKey, T : CurveType.Curve25519 =
    getKeyWithPrivateMaterialOrCreate(T::class)
}
