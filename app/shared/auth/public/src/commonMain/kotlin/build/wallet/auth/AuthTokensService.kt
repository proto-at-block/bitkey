package build.wallet.auth

import build.wallet.bitkey.f8e.AccountId
import build.wallet.f8e.F8eEnvironment
import com.github.michaelbull.result.Result

/**
 * Service for managing authentication tokens.
 * Allows to read and write tokens by account ID and authentication scope.
 * Also allows to refresh existing access token.
 */
interface AuthTokensService {
  /**
   * Refreshes the account's Access token by looking up the current account, whether it is
   * an active account, an account going through onboarding, or an account going through
   * recovery, and using the [AppAuthPublicKey] based on the given [AuthTokenScope].
   *
   * Note: this only works for app-generated auth public keys (i.e. [AppAuthPublicKey], not
   * [HwAuthPublicKey]).
   */
  suspend fun refreshAccessTokenWithApp(
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
  suspend fun getTokens(
    accountId: AccountId,
    scope: AuthTokenScope,
  ): Result<AccountAuthTokens?, Throwable>

  /**
   * Store auth tokens for given account ID and auth scope.
   */
  suspend fun setTokens(
    accountId: AccountId,
    tokens: AccountAuthTokens,
    scope: AuthTokenScope,
  ): Result<Unit, Throwable>

  /**
   * Deletes all local tokens. Used for testing and debugging purposes.
   * This will return error in Customer builds.
   */
  suspend fun clear(): Result<Unit, Throwable>
}
