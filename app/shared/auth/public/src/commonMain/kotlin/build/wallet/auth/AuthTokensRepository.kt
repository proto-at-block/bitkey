package build.wallet.auth

import build.wallet.bitkey.f8e.AccountId
import build.wallet.f8e.F8eEnvironment
import com.github.michaelbull.result.Result

/**
 * Repository for performing authentication operations.
 */
interface AuthTokensRepository {
  /**
   * Refreshes the account's Access token by looking up the current account, whether it is
   * an active account, an account going through onboarding, or an account going through
   * recovery, and using the [AppAuthPublicKey] based on the given [AuthTokenScope].
   *
   * Note: this only works for app-generated auth public keys (i.e. [AppAuthPublicKey], not
   * [HwAuthPublicKey]).
   */
  suspend fun refreshAccessToken(
    f8eEnvironment: F8eEnvironment,
    accountId: AccountId,
    scope: AuthTokenScope,
  ): Result<AccountAuthTokens, Error>

  /**
   * Returns auth tokens stored in the app for the given account for the given scope.
   *
   * Note that:
   * - for Full Account:
   *    - for Global scope: returns [AppGlobalAuthPublicKey].
   *    - for RecoveryApp scope: returns [AppRecoveryAuthPublicKey].
   * - for Lite Account: returns [AppRecoveryAuthPublicKey].
   */
  suspend fun getAuthTokens(
    accountId: AccountId,
    scope: AuthTokenScope,
  ): Result<AccountAuthTokens?, Throwable>
}
