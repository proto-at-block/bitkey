package build.wallet.auth

import app.cash.turbine.Turbine
import build.wallet.auth.AccountAuthenticator.AuthData
import build.wallet.bitkey.app.AppAuthKey
import build.wallet.bitkey.f8e.FullAccountId
import build.wallet.bitkey.f8e.FullAccountIdMock
import build.wallet.crypto.PublicKey
import build.wallet.f8e.F8eEnvironment
import build.wallet.f8e.auth.AuthF8eClient
import build.wallet.f8e.auth.AuthF8eClientMock
import com.github.michaelbull.result.Err
import com.github.michaelbull.result.Ok
import com.github.michaelbull.result.Result
import com.github.michaelbull.result.mapError

class AccountAuthenticatorMock(
  turbine: (String) -> Turbine<Any>,
  private val authF8eClient: AuthF8eClient = AuthF8eClientMock(),
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

  val defaultErrorAuthResult: Result<AuthData, AuthError> =
    Err(AuthProtocolError("error"))

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
    return authF8eClient.refreshToken(f8eEnvironment, refreshToken)
      .mapError {
        AuthProtocolError("Could not refresh tokens")
      }
  }

  fun reset() {
    authResults = mutableListOf(defaultAuthResult)
  }
}
