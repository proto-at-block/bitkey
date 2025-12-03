package build.wallet.bitcoin

import build.wallet.bitcoin.keys.ExtendedPrivateKey
import build.wallet.bitkey.app.AppAuthKey
import build.wallet.bitkey.app.AppSpendingKeypair
import build.wallet.bitkey.app.AppSpendingPrivateKey
import build.wallet.bitkey.app.AppSpendingPublicKey
import build.wallet.bitkey.keys.app.AppKey
import build.wallet.catchingResult
import build.wallet.crypto.KeyPurpose
import build.wallet.crypto.PrivateKey
import build.wallet.crypto.PublicKey
import build.wallet.di.AppScope
import build.wallet.di.BitkeyInject
import build.wallet.logging.*
import build.wallet.logging.logFailure
import build.wallet.mapUnit
import build.wallet.store.*
import com.github.michaelbull.result.Err
import com.github.michaelbull.result.Result
import com.github.michaelbull.result.coroutines.coroutineBinding
import com.github.michaelbull.result.flatMap
import com.github.michaelbull.result.map
import com.github.michaelbull.result.toErrorIf
import okio.ByteString.Companion.decodeHex

/**
 * A dao for storing and retrieving the app's generated private key.
 *
 * Maintains a dictionary of all stored private keys, using the associated
 * public key as the identifier.
 */

@BitkeyInject(AppScope::class)
class AppPrivateKeyDaoImpl(
  private val encryptedKeyValueStoreFactory: EncryptedKeyValueStoreFactory,
) : AppPrivateKeyDao {
  private suspend fun secureStore() =
    encryptedKeyValueStoreFactory.getOrCreate(AppPrivateKeyDao.STORE_NAME)

  private enum class PrivateKeyFields {
    SECRET_KEY {
      override fun prefix(): String = "secret-key"
    },
    MNEMONIC {
      override fun prefix(): String = "mnemonic"
    },
    ;

    fun hashKey(publicKey: String): String {
      return "${prefix()}:$publicKey"
    }

    abstract fun prefix(): String
  }

  override suspend fun storeAppSpendingKeyPair(
    keyPair: AppSpendingKeypair,
  ): Result<Unit, Throwable> {
    val secureStore = secureStore()
    return catchingResult {
      val secretKeyHashKey = PrivateKeyFields.SECRET_KEY.hashKey(keyPair.publicKey.key.dpub)
      val mnemonicHashKey = PrivateKeyFields.MNEMONIC.hashKey(keyPair.publicKey.key.dpub)

      // store both the secret key and the mnemonic
      secureStore.putString(key = secretKeyHashKey, value = keyPair.privateKey.key.xprv)
      secureStore.putString(key = mnemonicHashKey, value = keyPair.privateKey.key.mnemonic)
    }
      .flatMap {
        // Double check that the value was stored and fatal error otherwise.
        getAppSpendingPrivateKey(keyPair.publicKey)
          .toErrorIf(
            predicate = { privateKey -> privateKey != keyPair.privateKey },
            transform = {
              IllegalStateException("App spending private key unable to be stored in ${AppPrivateKeyDao.STORE_NAME}")
            }
          )
      }
      .mapUnit()
      .logFailure { "Failed to store spending key in ${AppPrivateKeyDao.STORE_NAME}" }
  }

  override suspend fun <T : AppAuthKey> storeAppKeyPair(
    keyPair: AppKey<T>,
  ): Result<Unit, Throwable> {
    return secureStore()
      .putStringWithResult(
        key = keyPair.publicKey.value,
        value = keyPair.privateKey.bytes.hex()
      )
      .flatMap {
        // Double check that the keys were properly stored, otherwise return error.
        val result = getAsymmetricPrivateKey(keyPair.publicKey)

        result
          .toErrorIf(
            predicate = { privateKey -> privateKey != keyPair.privateKey },
            transform = {
              IllegalStateException("App auth private key unable to be stored in ${AppPrivateKeyDao.STORE_NAME}")
            }
          )
      }
      .mapUnit()
      .logFailure { "Failed to store auth key in ${AppPrivateKeyDao.STORE_NAME}" }
  }

  override suspend fun <T : KeyPurpose> storeAsymmetricPrivateKey(
    publicKey: PublicKey<T>,
    privateKey: PrivateKey<T>,
  ): Result<Unit, Throwable> {
    return secureStore()
      .putStringWithResult(
        key = publicKey.value,
        value = privateKey.bytes.hex()
      )
      .flatMap {
        getAsymmetricPrivateKey(publicKey)
          .toErrorIf(
            predicate = { savedPrivateKey -> savedPrivateKey != privateKey },
            transform = {
              IllegalStateException("Asymmetric key unable to be stored in ${AppPrivateKeyDao.STORE_NAME}")
            }
          )
      }
      .mapUnit()
      .logFailure { "Failed to store asymmetric key in ${AppPrivateKeyDao.STORE_NAME}" }
  }

  override suspend fun getAppSpendingPrivateKey(
    publicKey: AppSpendingPublicKey,
  ): Result<AppSpendingPrivateKey?, Throwable> {
    return coroutineBinding {
      val secretKeyHashKey = PrivateKeyFields.SECRET_KEY.hashKey(publicKey.key.dpub)
      val mnemonicHashKey = PrivateKeyFields.MNEMONIC.hashKey(publicKey.key.dpub)

      val secureStore = secureStore()
      val secretKey = secureStore.getStringOrNullWithResult(secretKeyHashKey).bind()
      val mnemonic = secureStore.getStringOrNullWithResult(mnemonicHashKey).bind()

      if (secretKey != null && mnemonic != null) {
        val privateKey =
          ExtendedPrivateKey(
            xprv = secretKey,
            mnemonic = mnemonic
          )
        AppSpendingPrivateKey(privateKey)
      } else if (secretKey != null && mnemonic == null) {
        Err(MnemonicMissingError(message = "Mnemonic missing for $publicKey"))
          .bind<AppSpendingPrivateKey?>()
      } else if (secretKey == null && mnemonic != null) {
        Err(PrivateKeyMissingError(message = "Private key missing for $publicKey"))
          .bind<AppSpendingPrivateKey?>()
      } else {
        null
      }
    }.logFailure { "Failed to get private key from ${AppPrivateKeyDao.STORE_NAME}" }
  }

  override suspend fun getAllAppSpendingKeyPairs(): Result<List<AppSpendingKeypair>, Throwable> {
    return catchingResult {
      val secureStore = secureStore()
      val allKeys = secureStore.keys()

      // Get all spending public keys
      val publicKeys = allKeys
        .filter { it.startsWith(PrivateKeyFields.MNEMONIC.prefix()) }
        .map { it.removePrefix("${PrivateKeyFields.MNEMONIC.prefix()}:") }
        .toSet()

      // Build list of key pairs
      publicKeys.mapNotNull { publicKey ->
        val secretKeyHashKey = PrivateKeyFields.SECRET_KEY.hashKey(publicKey)
        val mnemonicHashKey = PrivateKeyFields.MNEMONIC.hashKey(publicKey)

        val secretKey = secureStore.getStringOrNull(secretKeyHashKey)
        val mnemonic = secureStore.getStringOrNull(mnemonicHashKey)

        if (secretKey != null && mnemonic != null) {
          val privateKey = ExtendedPrivateKey(xprv = secretKey, mnemonic = mnemonic)
          val appPrivateKey = AppSpendingPrivateKey(privateKey)

          val appPublicKey = AppSpendingPublicKey(publicKey)
          AppSpendingKeypair(privateKey = appPrivateKey, publicKey = appPublicKey)
        } else {
          null
        }
      }
    }.logFailure { "Failed to get all app spending key pairs from ${AppPrivateKeyDao.STORE_NAME}" }
  }

  override suspend fun <T : KeyPurpose> getAsymmetricPrivateKey(
    key: PublicKey<T>,
  ): Result<PrivateKey<T>?, Throwable> {
    return secureStore()
      .getStringOrNullWithResult(key.value)
      .map { it?.let { PrivateKey<T>(it.decodeHex()) } }
      .logFailure { "Error getting asymmetric private key from ${AppPrivateKeyDao.STORE_NAME}" }
  }

  override suspend fun remove(key: AppSpendingPublicKey): Result<Unit, Throwable> =
    coroutineBinding {
      val secureStore = secureStore()
      val secretKeyHashKey = PrivateKeyFields.SECRET_KEY.hashKey(key.key.dpub)
      val mnemonicHashKey = PrivateKeyFields.MNEMONIC.hashKey(key.key.dpub)
      secureStore.removeWithResult(key = secretKeyHashKey).bind()
      secureStore.removeWithResult(key = mnemonicHashKey).bind()
    }
      .logFailure { "Failed to remove spending key in ${AppPrivateKeyDao.STORE_NAME}" }

  override suspend fun <T : KeyPurpose> remove(key: PublicKey<T>): Result<Unit, Throwable> {
    return secureStore()
      .removeWithResult(key = key.value)
      .logFailure { "Error removing asymmetric key in ${AppPrivateKeyDao.STORE_NAME}" }
  }

  override suspend fun clear(): Result<Unit, Throwable> {
    logWarn { "Clearing app private keys!" }

    return secureStore()
      .clearWithResult()
      .logFailure { "Error clearing app private spending and auth keys" }
  }
}

class PrivateKeyMissingError(override val message: String) : Error()

class MnemonicMissingError(override val message: String) : Error()
