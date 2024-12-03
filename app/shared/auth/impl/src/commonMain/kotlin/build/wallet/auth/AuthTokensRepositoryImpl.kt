package build.wallet.auth

import build.wallet.bitkey.f8e.AccountId
import build.wallet.f8e.F8eEnvironment
import com.github.michaelbull.result.Result

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
    return authTokenDao.getTokensOfScope(accountId, scope)
  }
}
