package build.wallet.auth

import build.wallet.bitkey.f8e.AccountId
import build.wallet.f8e.F8eEnvironment
import build.wallet.logging.LogLevel
import build.wallet.logging.log
import com.github.michaelbull.result.Result
import com.github.michaelbull.result.onSuccess

class AuthTokensRepositoryImpl(
  private val authTokenDao: AuthTokenDao,
  private val authTokenRefresher: AppAuthTokenRefresher,
) : AuthTokensRepository {
  override suspend fun refreshAccessToken(
    f8eEnvironment: F8eEnvironment,
    accountId: AccountId,
    scope: AuthTokenScope,
  ): Result<AccountAuthTokens, Error> {
    return authTokenRefresher.refreshAccessTokenForAccount(f8eEnvironment, accountId, scope)
  }

  override suspend fun getAuthTokens(
    accountId: AccountId,
    scope: AuthTokenScope,
  ): Result<AccountAuthTokens?, Throwable> {
    log(LogLevel.Debug) { "Loading auth tokens for $accountId..." }
    return authTokenDao
      .getTokensOfScope(accountId, scope)
      .onSuccess { tokens ->
        if (tokens == null) {
          log(LogLevel.Warn) { "No auth tokens found for $accountId of scope $scope" }
        } else {
          log(LogLevel.Debug) { "Found auth tokens for $accountId of scope $scope" }
        }
      }
  }
}
