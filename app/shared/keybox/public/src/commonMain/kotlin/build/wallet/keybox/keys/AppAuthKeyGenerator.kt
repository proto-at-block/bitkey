package build.wallet.keybox.keys

import build.wallet.bitkey.app.AppGlobalAuthKeypair
import build.wallet.bitkey.app.AppRecoveryAuthKeypair
import com.github.michaelbull.result.Result

interface AppAuthKeyGenerator {
  /**
   * Generates a new [AppGlobalAuthKeypair] to be used by app factor for global authentication
   * scope.
   *
   * Note that the private key is not stored anywhere, so it is the responsibility of the caller
   * to store it securely.
   */
  suspend fun generateGlobalAuthKey(): Result<AppGlobalAuthKeypair, Throwable>

  /**
   * Generates a new [AppGlobalAuthKeypair] to be used by app factor for "recovery" authentication
   * scope.
   *
   * Note that the private key is not stored anywhere, so it is the responsibility of the caller
   * to store it securely.
   */
  suspend fun generateRecoveryAuthKey(): Result<AppRecoveryAuthKeypair, Throwable>
}
