package build.wallet.auth

import build.wallet.bitkey.f8e.AccountId
import build.wallet.f8e.F8eEnvironment
import com.github.michaelbull.result.Ok
import com.github.michaelbull.result.Result

class AuthTokensRepositoryMock : AuthTokensRepository {
  var authTokensResult: Result<AccountAuthTokens, Error> = Ok(AccountAuthTokensMock)

  override suspend fun refreshAccessToken(
    f8eEnvironment: F8eEnvironment,
    accountId: AccountId,
    scope: AuthTokenScope,
  ): Result<AccountAuthTokens, Error> {
    return authTokensResult
  }

  override suspend fun getAuthTokens(
    accountId: AccountId,
    scope: AuthTokenScope,
  ): Result<AccountAuthTokens?, Throwable> {
    return authTokensResult
  }

  fun reset() {
    authTokensResult = Ok(AccountAuthTokensMock)
  }
}
