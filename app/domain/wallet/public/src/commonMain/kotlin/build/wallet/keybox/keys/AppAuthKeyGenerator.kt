package build.wallet.keybox.keys

import build.wallet.bitkey.app.AppGlobalAuthKey
import build.wallet.bitkey.app.AppRecoveryAuthKey
import build.wallet.bitkey.keys.app.AppKey
import com.github.michaelbull.result.Result

interface AppAuthKeyGenerator {
  /**
   * Generates a new [AppGlobalAuthKey] to be used by app factor for global authentication
   * scope.
   *
   * Note that the private key is not stored anywhere, so it is the responsibility of the caller
   * to store it securely.
   */
  suspend fun generateGlobalAuthKey(): Result<AppKey<AppGlobalAuthKey>, Throwable>

  /**
   * Generates a new [AppGlobalAuthKey] to be used by app factor for "recovery" authentication
   * scope.
   *
   * Note that the private key is not stored anywhere, so it is the responsibility of the caller
   * to store it securely.
   */
  suspend fun generateRecoveryAuthKey(): Result<AppKey<AppRecoveryAuthKey>, Throwable>
}
