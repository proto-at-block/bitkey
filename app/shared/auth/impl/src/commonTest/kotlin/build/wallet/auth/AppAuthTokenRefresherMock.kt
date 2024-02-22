package build.wallet.auth

import build.wallet.bitkey.f8e.AccountId
import build.wallet.f8e.F8eEnvironment
import com.github.michaelbull.result.Ok
import com.github.michaelbull.result.Result

class AppAuthTokenRefresherMock : AppAuthTokenRefresher {
  override suspend fun refreshAccessTokenForAccount(
    f8eEnvironment: F8eEnvironment,
    accountId: AccountId,
    tokenScope: AuthTokenScope,
  ): Result<AccountAuthTokens, AuthError> {
    return Ok(
      AccountAuthTokens(
        accessToken = AccessToken(""),
        refreshToken = RefreshToken("")
      )
    )
  }
}
