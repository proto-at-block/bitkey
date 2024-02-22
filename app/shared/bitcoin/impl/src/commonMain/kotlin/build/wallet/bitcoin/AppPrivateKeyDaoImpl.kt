@file:OptIn(ExperimentalSettingsApi::class)

package build.wallet.bitcoin

import build.wallet.bitcoin.keys.ExtendedPrivateKey
import build.wallet.bitkey.app.AppAuthKeypair
import build.wallet.bitkey.app.AppAuthPublicKey
import build.wallet.bitkey.app.AppGlobalAuthKeypair
import build.wallet.bitkey.app.AppGlobalAuthPrivateKey
import build.wallet.bitkey.app.AppGlobalAuthPublicKey
import build.wallet.bitkey.app.AppRecoveryAuthKeypair
import build.wallet.bitkey.app.AppRecoveryAuthPrivateKey
import build.wallet.bitkey.app.AppRecoveryAuthPublicKey
import build.wallet.bitkey.app.AppSpendingKeypair
import build.wallet.bitkey.app.AppSpendingPrivateKey
import build.wallet.bitkey.app.AppSpendingPublicKey
import build.wallet.catching
import build.wallet.crypto.PrivateKey
import build.wallet.crypto.PublicKey
import build.wallet.encrypt.Secp256k1PrivateKey
import build.wallet.logging.log
import build.wallet.logging.logFailure
import build.wallet.mapUnit
import build.wallet.store.EncryptedKeyValueStoreFactory
import build.wallet.store.clearWithResult
import build.wallet.store.getStringOrNullWithResult
import build.wallet.store.putStringWithResult
import build.wallet.store.removeWithResult
import com.github.michaelbull.result.Err
import com.github.michaelbull.result.Result
import com.github.michaelbull.result.coroutines.binding.binding
import com.github.michaelbull.result.flatMap
import com.github.michaelbull.result.map
import com.github.michaelbull.result.toErrorIf
import com.russhwolf.settings.ExperimentalSettingsApi
import okio.ByteString.Companion.decodeHex

/**
 * A dao for storing and retrieving the app's generated private key.
 *
 * Maintains a dictionary of all stored private keys, using the associated
 * public key as the identifier.
 */
class AppPrivateKeyDaoImpl(
  private val encryptedKeyValueStoreFactory: EncryptedKeyValueStoreFactory,
) : AppPrivateKeyDao {
  private suspend fun secureStore() = encryptedKeyValueStoreFactory.getOrCreate(STORE_NAME)

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
    return Result
      .catching {
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
              IllegalStateException("App spending private key unable to be stored in $STORE_NAME")
            }
          )
      }
      .mapUnit()
      .logFailure { "Failed to store spending key in $STORE_NAME" }
  }

  override suspend fun storeAppAuthKeyPair(keyPair: AppAuthKeypair): Result<Unit, Throwable> {
    return secureStore()
      .putStringWithResult(
        key = keyPair.publicKey.pubKey.value,
        value = keyPair.privateKey.key.bytes.hex()
      )
      .flatMap {
        // Double check that the keys were properly stored, otherwise return error.
        val result =
          when (keyPair) {
            is AppGlobalAuthKeypair -> getGlobalAuthKey(keyPair.publicKey)
            is AppRecoveryAuthKeypair -> getRecoveryAuthKey(keyPair.publicKey)
          }

        result
          .toErrorIf(
            predicate = { privateKey -> privateKey != keyPair.privateKey },
            transform = {
              IllegalStateException("App auth private key unable to be stored in $STORE_NAME")
            }
          )
      }
      .mapUnit()
      .logFailure { "Failed to store auth key in $STORE_NAME" }
  }

  override suspend fun storeAsymmetricPrivateKey(
    publicKey: PublicKey,
    privateKey: PrivateKey,
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
              IllegalStateException("Asymmetric key unable to be stored in $STORE_NAME")
            }
          )
      }
      .mapUnit()
      .logFailure { "Failed to store asymmetric key in $STORE_NAME" }
  }

  override suspend fun getAppSpendingPrivateKey(
    publicKey: AppSpendingPublicKey,
  ): Result<AppSpendingPrivateKey?, Throwable> {
    return binding {
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
    }.logFailure { "Failed to get private key from $STORE_NAME" }
  }

  override suspend fun getGlobalAuthKey(
    publicKey: AppGlobalAuthPublicKey,
  ): Result<AppGlobalAuthPrivateKey?, Throwable> {
    return secureStore()
      .getStringOrNullWithResult(publicKey.pubKey.value)
      .map { it?.let { AppGlobalAuthPrivateKey(Secp256k1PrivateKey(it.decodeHex())) } }
      .logFailure { "Error getting app global auth private key from $STORE_NAME" }
  }

  override suspend fun getRecoveryAuthKey(
    publicKey: AppRecoveryAuthPublicKey,
  ): Result<AppRecoveryAuthPrivateKey?, Throwable> {
    return secureStore()
      .getStringOrNullWithResult(publicKey.pubKey.value)
      .map { it?.let { AppRecoveryAuthPrivateKey(Secp256k1PrivateKey(it.decodeHex())) } }
      .logFailure { "Error getting app global auth private key from $STORE_NAME" }
  }

  override suspend fun getAsymmetricPrivateKey(key: PublicKey): Result<PrivateKey?, Throwable> {
    return secureStore()
      .getStringOrNullWithResult(key.value)
      .map { it?.let { PrivateKey(it.decodeHex()) } }
      .logFailure { "Error getting asymmetric private key from $STORE_NAME" }
  }

  override suspend fun remove(key: AppSpendingPublicKey): Result<Unit, Throwable> =
    binding {
      val secureStore = secureStore()
      val secretKeyHashKey = PrivateKeyFields.SECRET_KEY.hashKey(key.key.dpub)
      val mnemonicHashKey = PrivateKeyFields.MNEMONIC.hashKey(key.key.dpub)
      secureStore.removeWithResult(key = secretKeyHashKey).bind()
      secureStore.removeWithResult(key = mnemonicHashKey).bind()
    }
      .logFailure { "Failed to remove spending key in $STORE_NAME" }

  override suspend fun remove(key: AppAuthPublicKey): Result<Unit, Throwable> {
    return secureStore()
      .removeWithResult(key = key.pubKey.value)
      .logFailure { "Error removing auth key in $STORE_NAME" }
  }

  override suspend fun remove(key: PublicKey): Result<Unit, Throwable> {
    return secureStore()
      .removeWithResult(key = key.value)
      .logFailure { "Error removing asymmetric key in $STORE_NAME" }
  }

  override suspend fun clear(): Result<Unit, Throwable> {
    log { "Clearing app private keys!" }

    return secureStore()
      .clearWithResult()
      .logFailure { "Error clearing app private spending and auth keys" }
  }

  private companion object {
    const val STORE_NAME = "AppPrivateKeyStore"
  }
}

class PrivateKeyMissingError(override val message: String) : Error()

class MnemonicMissingError(override val message: String) : Error()
