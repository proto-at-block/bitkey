package build.wallet.f8e.auth

import bitkey.auth.AccountAuthTokens
import bitkey.auth.AuthTokenScope
import bitkey.auth.RefreshToken
import build.wallet.bitkey.app.AppAuthKey
import build.wallet.bitkey.hardware.HwAuthPublicKey
import build.wallet.crypto.PublicKey
import build.wallet.f8e.F8eEnvironment
import build.wallet.f8e.auth.AuthF8eClient.InitiateAuthenticationSuccess
import build.wallet.f8e.auth.AuthF8eClient.InitiateHardwareAuthenticationSuccess
import build.wallet.ktor.result.HttpError.UnhandledException
import build.wallet.ktor.result.NetworkingError
import com.github.michaelbull.result.Err
import com.github.michaelbull.result.Result

class AuthF8eClientMock(
  var defaultRefreshResult: Result<AccountAuthTokens, NetworkingError> =
    Err(
      UnhandledException(NotImplementedError())
    ),
  var defaultInitiateAuthenticationResult: Result<InitiateAuthenticationSuccess, NetworkingError> =
    Err(
      UnhandledException(NotImplementedError())
    ),
  var defaultCompleteAuthenticationResult: Result<AccountAuthTokens, NetworkingError> =
    Err(
      UnhandledException(NotImplementedError())
    ),
  var defaultInitiateHardwareAuthenticationResult:
    Result<InitiateHardwareAuthenticationSuccess, NetworkingError> =
    Err(
      UnhandledException(NotImplementedError())
    ),
) : AuthF8eClient {
  var refreshResult = defaultRefreshResult
  var initiateAuthenticationResult = defaultInitiateAuthenticationResult
  var completeAuthenticationResult = defaultCompleteAuthenticationResult
  var initiateHardwareAuthenticationResult = defaultInitiateHardwareAuthenticationResult

  override suspend fun initiateAuthentication(
    f8eEnvironment: F8eEnvironment,
    authPublicKey: HwAuthPublicKey,
  ): Result<InitiateAuthenticationSuccess, NetworkingError> {
    return initiateAuthenticationResult
  }

  override suspend fun initiateAuthentication(
    f8eEnvironment: F8eEnvironment,
    authPublicKey: PublicKey<out AppAuthKey>,
    tokenScope: AuthTokenScope,
  ): Result<InitiateAuthenticationSuccess, NetworkingError> = initiateAuthenticationResult

  override suspend fun initiateHardwareAuthentication(
    f8eEnvironment: F8eEnvironment,
    authPublicKey: HwAuthPublicKey,
  ): Result<InitiateHardwareAuthenticationSuccess, NetworkingError> {
    return initiateHardwareAuthenticationResult
  }

  override suspend fun completeAuthentication(
    f8eEnvironment: F8eEnvironment,
    username: String,
    challengeResponse: String,
    session: String,
  ): Result<AccountAuthTokens, NetworkingError> {
    return completeAuthenticationResult
  }

  override suspend fun refreshToken(
    f8eEnvironment: F8eEnvironment,
    refreshToken: RefreshToken,
  ): Result<AccountAuthTokens, NetworkingError> {
    return refreshResult
  }

  fun reset() {
    refreshResult = defaultRefreshResult
    initiateAuthenticationResult = defaultInitiateAuthenticationResult
    completeAuthenticationResult = defaultCompleteAuthenticationResult
    initiateHardwareAuthenticationResult = defaultInitiateHardwareAuthenticationResult
  }
}
