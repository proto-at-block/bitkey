package build.wallet.auth

import app.cash.turbine.Turbine
import build.wallet.auth.AccountAuthenticator.AuthData
import build.wallet.bitkey.app.AppAuthKey
import build.wallet.bitkey.f8e.FullAccountId
import build.wallet.bitkey.f8e.FullAccountIdMock
import build.wallet.crypto.PublicKey
import build.wallet.f8e.F8eEnvironment
import build.wallet.f8e.auth.AuthenticationService
import build.wallet.f8e.auth.AuthenticationServiceMock
import com.github.michaelbull.result.Err
import com.github.michaelbull.result.Ok
import com.github.michaelbull.result.Result

class AccountAuthenticatorMock(
  turbine: (String) -> Turbine<Any>,
  private val authenticationService: AuthenticationService = AuthenticationServiceMock(),
) : AccountAuthenticator {
  val defaultAuthResult: Result<AuthData, AuthError> =
    Ok(
      AuthData(
        accountId = FullAccountIdMock.serverId,
        authTokens =
          AccountAuthTokens(
            accessToken = AccessToken("access-token-fake"),
            refreshToken = RefreshToken("refresh-token-fake")
          )
      )
    )

  var authResults: MutableList<Result<AuthData, AuthError>> = mutableListOf(defaultAuthResult)
  val authCalls = turbine("auth calls")

  override suspend fun appAuth(
    f8eEnvironment: F8eEnvironment,
    appAuthPublicKey: PublicKey<out AppAuthKey>,
    authTokenScope: AuthTokenScope,
  ): Result<AuthData, AuthError> {
    authCalls.add(appAuthPublicKey)
    return authResults.removeFirst()
  }

  override suspend fun hwAuth(
    f8eEnvironment: F8eEnvironment,
    fullAccountId: FullAccountId,
    session: String,
    signature: String,
  ): Result<AccountAuthTokens, AuthError> {
    return Ok(
      AccountAuthTokens(
        accessToken = AccessToken("access-token-fake"),
        refreshToken = RefreshToken("refresh-token-fake")
      )
    )
  }

  private suspend fun refresh(
    f8eEnvironment: F8eEnvironment,
    refreshToken: RefreshToken,
  ): Result<AccountAuthTokens, AuthError> {
    return when (val result = authenticationService.refreshToken(f8eEnvironment, refreshToken)) {
      is Err -> Err(AuthProtocolError("Could not refresh tokens"))
      is Ok -> result
    }
  }

  fun reset() {
    authResults = mutableListOf(defaultAuthResult)
  }
}
