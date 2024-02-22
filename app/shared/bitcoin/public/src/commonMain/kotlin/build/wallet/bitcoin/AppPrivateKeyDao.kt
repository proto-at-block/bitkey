package build.wallet.bitcoin

import build.wallet.bitkey.app.AppAuthKeypair
import build.wallet.bitkey.app.AppAuthPublicKey
import build.wallet.bitkey.app.AppGlobalAuthPrivateKey
import build.wallet.bitkey.app.AppGlobalAuthPublicKey
import build.wallet.bitkey.app.AppRecoveryAuthPrivateKey
import build.wallet.bitkey.app.AppRecoveryAuthPublicKey
import build.wallet.bitkey.app.AppSpendingKeypair
import build.wallet.bitkey.app.AppSpendingPrivateKey
import build.wallet.bitkey.app.AppSpendingPublicKey
import build.wallet.crypto.PrivateKey
import build.wallet.crypto.PublicKey
import com.github.michaelbull.result.Result

/**
 * A dao for storing and retrieving the app's generated private keys.
 *
 * Maintains a dictionary of all stored private keys, using the associated
 * public key as the identifier.
 */
@Suppress("TooManyFunctions")
interface AppPrivateKeyDao {
  suspend fun storeAppSpendingKeyPair(keyPair: AppSpendingKeypair): Result<Unit, Throwable>

  // TODO(BKR-626): Replace usages with storeAsymmetricPrivateKey
  suspend fun storeAppAuthKeyPair(keyPair: AppAuthKeypair): Result<Unit, Throwable>

  /**
   * Generic method for storing a [privateKey], indexed by its [publicKey]
   */
  suspend fun storeAsymmetricPrivateKey(
    publicKey: PublicKey,
    privateKey: PrivateKey,
  ): Result<Unit, Throwable>

  suspend fun getAppSpendingPrivateKey(
    publicKey: AppSpendingPublicKey,
  ): Result<AppSpendingPrivateKey?, Throwable>

  // TODO(BKR-626): Replace usages with storeAsymmetricPrivateKey
  suspend fun getGlobalAuthKey(
    publicKey: AppGlobalAuthPublicKey,
  ): Result<AppGlobalAuthPrivateKey?, Throwable>

  suspend fun getRecoveryAuthKey(
    publicKey: AppRecoveryAuthPublicKey,
  ): Result<AppRecoveryAuthPrivateKey?, Throwable>

  /**
   * Generic method for loading a [PrivateKey], indexed by its [PublicKey].
   */
  suspend fun getAsymmetricPrivateKey(key: PublicKey): Result<PrivateKey?, Throwable>

  suspend fun remove(key: AppSpendingPublicKey): Result<Unit, Throwable>

  suspend fun remove(key: AppAuthPublicKey): Result<Unit, Throwable>

  suspend fun remove(key: PublicKey): Result<Unit, Throwable>

  /**
   * Clears all stored private keys. This should only be used for testing purposes.
   */
  suspend fun clear(): Result<Unit, Throwable>
}
