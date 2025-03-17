package build.wallet.auth

import bitkey.auth.AuthTokenScope
import build.wallet.bitkey.app.AppAuthKey
import build.wallet.bitkey.f8e.AccountId
import build.wallet.crypto.PublicKey
import com.github.michaelbull.result.Result

/**
 * Provider for the [AppAuthPublicKey] looked up based on the current state of the app.
*/
interface AppAuthPublicKeyProvider {
  /**
   * Returns the [AppAuthPublicKey] corresponding to the current state of the app for the
   * given [bitkey.auth.AuthTokenScope].
   *
   * The 3 main states of the app that are checked here are:
   * - Active Account
   * - Onboarding Account
   * - Recovering Account
   * If none of those states are found, returns [AuthError.AccountMissing].
   * Otherwise, tries to find the [AppAuthPublicKey] that matches the [bitkey.auth.AuthTokenScope] on
   * the current account / recovery.
   */
  suspend fun getAppAuthPublicKeyFromAccountOrRecovery(
    accountId: AccountId,
    tokenScope: AuthTokenScope,
  ): Result<PublicKey<out AppAuthKey>, AuthError>
}
