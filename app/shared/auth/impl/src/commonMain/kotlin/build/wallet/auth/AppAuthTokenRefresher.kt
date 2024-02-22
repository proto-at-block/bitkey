package build.wallet.auth

import build.wallet.bitkey.f8e.AccountId
import build.wallet.f8e.F8eEnvironment
import com.github.michaelbull.result.Result

interface AppAuthTokenRefresher {
  /**
   * Refreshes access token of the given type with f8e for current account by looking up
   * the account information, whether it be an active account, an account still onboarding,
   * or an account performing recovery.
   */
  suspend fun refreshAccessTokenForAccount(
    f8eEnvironment: F8eEnvironment,
    accountId: AccountId,
    tokenScope: AuthTokenScope,
  ): Result<AccountAuthTokens, AuthError>
}
