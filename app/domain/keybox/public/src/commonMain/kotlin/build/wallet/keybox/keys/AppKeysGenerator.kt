package build.wallet.keybox.keys

import build.wallet.bitkey.app.AppGlobalAuthKey
import build.wallet.bitkey.app.AppKeyBundle
import build.wallet.bitkey.app.AppRecoveryAuthKey
import build.wallet.crypto.PublicKey
import com.github.michaelbull.result.Result

/**
 * All generated private keys are associated with a public key and are stored in the secure storage
 * using [AppPrivateKeyDao].
 */
interface AppKeysGenerator {
  /**
   * Generates a new set of keys based on a random master key, returns the KeyBundle with the public
   * keys.
   */
  suspend fun generateKeyBundle(): Result<AppKeyBundle, Throwable>

  /**
   * Generates new [AppGlobalAuthPublicKey] and [AppGlobalAuthPrivateKey].
   */
  suspend fun generateGlobalAuthKey(): Result<PublicKey<AppGlobalAuthKey>, Throwable>

  /**
   * Generates new [AppRecoveryAuthPublicKey] and [AppRecoveryAuthPrivateKey].
   */
  suspend fun generateRecoveryAuthKey(): Result<PublicKey<AppRecoveryAuthKey>, Throwable>
}
