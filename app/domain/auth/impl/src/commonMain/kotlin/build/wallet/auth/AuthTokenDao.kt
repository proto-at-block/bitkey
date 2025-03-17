package build.wallet.auth

import bitkey.auth.AccountAuthTokens
import bitkey.auth.AuthTokenScope
import build.wallet.bitkey.f8e.AccountId
import com.github.michaelbull.result.Result

/**
 * A dao for storing and retrieving app's authentication tokens, keyed by a user's `accountId`
 */
interface AuthTokenDao {
  /**
   * Retrieves the tokens for the given account of the given scope.
   */
  suspend fun getTokensOfScope(
    accountId: AccountId,
    scope: AuthTokenScope,
  ): Result<AccountAuthTokens?, Throwable>

  /**
   * Sets the given tokens for the given account and given scope.
   */
  suspend fun setTokensOfScope(
    accountId: AccountId,
    tokens: AccountAuthTokens,
    scope: AuthTokenScope,
  ): Result<Unit, Throwable>

  /**
   * Clears the store
   */
  suspend fun clear(): Result<Unit, Throwable>
}
