package build.wallet.auth

import app.cash.turbine.Turbine
import bitkey.auth.AccessToken
import bitkey.auth.AccountAuthTokens
import bitkey.auth.AuthTokenScope
import bitkey.auth.RefreshToken
import build.wallet.auth.AccountAuthenticator.AuthData
import build.wallet.bitkey.app.AppAuthKey
import build.wallet.bitkey.f8e.FullAccountId
import build.wallet.bitkey.f8e.FullAccountIdMock
import build.wallet.crypto.PublicKey
import com.github.michaelbull.result.Err
import com.github.michaelbull.result.Ok
import com.github.michaelbull.result.Result
import kotlinx.datetime.Instant

class AccountAuthenticatorMock(
  turbine: (String) -> Turbine<Any>,
) : AccountAuthenticator {
  val defaultAuthResult: Result<AuthData, AuthError> =
    Ok(
      AuthData(
        accountId = FullAccountIdMock.serverId,
        authTokens =
          AccountAuthTokens(
            accessToken = AccessToken("access-token-fake"),
            refreshToken = RefreshToken("refresh-token-fake"),
            accessTokenExpiresAt = Instant.DISTANT_FUTURE
          )
      )
    )

  val defaultErrorAuthResult: Result<AuthData, AuthError> =
    Err(AuthProtocolError("error"))

  var authResults: MutableList<Result<AuthData, AuthError>> = mutableListOf(defaultAuthResult)
  val authCalls = turbine("auth calls")

  override suspend fun appAuth(
    appAuthPublicKey: PublicKey<out AppAuthKey>,
    authTokenScope: AuthTokenScope,
  ): Result<AuthData, AuthError> {
    authCalls.add(appAuthPublicKey)
    return authResults.removeFirst()
  }

  override suspend fun hwAuth(
    fullAccountId: FullAccountId,
    session: String,
    signature: String,
  ): Result<AccountAuthTokens, AuthError> {
    return Ok(
      AccountAuthTokens(
        accessToken = AccessToken("access-token-fake"),
        refreshToken = RefreshToken("refresh-token-fake"),
        accessTokenExpiresAt = Instant.DISTANT_FUTURE
      )
    )
  }

  fun reset() {
    authResults = mutableListOf(defaultAuthResult)
  }
}
