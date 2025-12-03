package build.wallet.bitcoin

import build.wallet.bitkey.app.AppAuthKey
import build.wallet.bitkey.app.AppSpendingKeypair
import build.wallet.bitkey.app.AppSpendingPrivateKey
import build.wallet.bitkey.app.AppSpendingPublicKey
import build.wallet.bitkey.keys.app.AppKey
import build.wallet.crypto.KeyPurpose
import build.wallet.crypto.PrivateKey
import build.wallet.crypto.PublicKey
import com.github.michaelbull.result.Result

/**
 * A dao for storing and retrieving the app's generated private keys.
 *
 * Maintains a dictionary of all stored private keys, using the associated
 * public key as the identifier.
 */
interface AppPrivateKeyDao {
  suspend fun storeAppSpendingKeyPair(keyPair: AppSpendingKeypair): Result<Unit, Throwable>

  suspend fun <T : AppAuthKey> storeAppKeyPair(keyPair: AppKey<T>) =
    storeAsymmetricPrivateKey(
      publicKey = keyPair.publicKey,
      privateKey = keyPair.privateKey
    )

  /**
   * Generic method for storing a [privateKey], indexed by its [publicKey]
   */
  suspend fun <T : KeyPurpose> storeAsymmetricPrivateKey(
    publicKey: PublicKey<T>,
    privateKey: PrivateKey<T>,
  ): Result<Unit, Throwable>

  suspend fun getAppSpendingPrivateKey(
    publicKey: AppSpendingPublicKey,
  ): Result<AppSpendingPrivateKey?, Throwable>

  /**
   * Returns all available App spending key pairs stored in the DAO.
   */
  suspend fun getAllAppSpendingKeyPairs(): Result<List<AppSpendingKeypair>, Throwable>

  /**
   * Generic method for loading a [PrivateKey], indexed by its [PublicKey].
   */
  suspend fun <T : KeyPurpose> getAsymmetricPrivateKey(
    key: PublicKey<T>,
  ): Result<PrivateKey<T>?, Throwable>

  suspend fun remove(key: AppSpendingPublicKey): Result<Unit, Throwable>

  suspend fun <T : KeyPurpose> remove(key: PublicKey<T>): Result<Unit, Throwable>

  /**
   * Clears all stored private keys. This should only be used for testing purposes.
   */
  suspend fun clear(): Result<Unit, Throwable>

  companion object {
    const val STORE_NAME = "AppPrivateKeyStore"
  }
}
