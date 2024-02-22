package build.wallet.keybox.keys

import build.wallet.bitcoin.BitcoinNetworkType
import build.wallet.bitkey.app.AppGlobalAuthPrivateKey
import build.wallet.bitkey.app.AppGlobalAuthPublicKey
import build.wallet.bitkey.app.AppKeyBundle
import build.wallet.bitkey.app.AppRecoveryAuthPrivateKey
import build.wallet.bitkey.app.AppRecoveryAuthPublicKey
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
  suspend fun generateKeyBundle(network: BitcoinNetworkType): Result<AppKeyBundle, Throwable>

  /**
   * Generates new [AppGlobalAuthPublicKey] and [AppGlobalAuthPrivateKey].
   */
  suspend fun generateGlobalAuthKey(): Result<AppGlobalAuthPublicKey, Throwable>

  /**
   * Generates new [AppRecoveryAuthPublicKey] and [AppRecoveryAuthPrivateKey].
   */
  suspend fun generateRecoveryAuthKey(): Result<AppRecoveryAuthPublicKey, Throwable>
}
