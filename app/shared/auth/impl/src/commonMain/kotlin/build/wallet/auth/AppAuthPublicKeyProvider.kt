package build.wallet.auth

import build.wallet.bitkey.app.AppAuthPublicKey
import build.wallet.bitkey.f8e.AccountId
import build.wallet.f8e.F8eEnvironment
import com.github.michaelbull.result.Result

/**
 * Provider for the [AppAuthPublicKey] looked up based on the current state of the app.
*/
interface AppAuthPublicKeyProvider {
  /**
   * Returns the [AppAuthPublicKey] corresponding to the current state of the app for the
   * given [AuthTokenScope].
   *
   * The 3 main states of the app that are checked here are:
   * - Active Account
   * - Onboarding Account
   * - Recovering Account
   * If none of those states are found, returns [AuthError.AccountMissing].
   * Otherwise, tries to find the [AppAuthPublicKey] that matches the [AuthTokenScope] on
   * the current account / recovery.
   */
  suspend fun getAppAuthPublicKeyFromAccountOrRecovery(
    f8eEnvironment: F8eEnvironment,
    accountId: AccountId,
    tokenScope: AuthTokenScope,
  ): Result<AppAuthPublicKey, AuthError>
}
