package build.wallet.auth

import build.wallet.auth.AccountAuthenticator.AuthData
import build.wallet.bitkey.app.AppAuthKey
import build.wallet.bitkey.f8e.FullAccountId
import build.wallet.crypto.PublicKey
import build.wallet.f8e.F8eEnvironment
import build.wallet.f8e.auth.AuthenticationService
import build.wallet.ktor.result.HttpError
import build.wallet.logging.LogLevel
import build.wallet.logging.log
import com.github.michaelbull.result.Result
import com.github.michaelbull.result.coroutines.binding.binding
import com.github.michaelbull.result.mapError
import io.ktor.http.HttpStatusCode.Companion.NotFound
import okio.ByteString.Companion.encodeUtf8

class AccountAuthenticatorImpl(
  private val appAuthKeyMessageSigner: AppAuthKeyMessageSigner,
  private val authenticationService: AuthenticationService,
) : AccountAuthenticator {
  override suspend fun appAuth(
    f8eEnvironment: F8eEnvironment,
    appAuthPublicKey: PublicKey<out AppAuthKey>,
    authTokenScope: AuthTokenScope,
  ): Result<AuthData, AuthError> =
    binding {
      log(level = LogLevel.Debug) {
        "Attempting to authenticate with app auth public key $appAuthPublicKey"
      }

      val signInResponse =
        authenticationService
          .initiateAuthentication(f8eEnvironment, appAuthPublicKey, authTokenScope)
          .mapError { error ->
            when (error) {
              is HttpError.ClientError -> if (error.response.status == NotFound) {
                AuthSignatureMismatch
              } else {
                AuthNetworkError("Could not sign in: ${error.message}", error)
              }
              else -> AuthNetworkError("Could not sign in: ${error.message}", error)
            }
          }
          .bind()

      val signature =
        appAuthKeyMessageSigner
          .signMessage(appAuthPublicKey, signInResponse.challenge.encodeUtf8())
          .mapError { AuthProtocolError("Cannot sign message: ${it.message}", it) }.bind()

      val authTokens =
        respondToAuthChallenge(
          f8eEnvironment,
          signInResponse.username,
          signInResponse.session,
          signature
        ).bind()

      AuthData(signInResponse.accountId, authTokens)
    }

  override suspend fun hwAuth(
    f8eEnvironment: F8eEnvironment,
    fullAccountId: FullAccountId,
    session: String,
    signature: String,
  ): Result<AccountAuthTokens, AuthError> {
    return respondToAuthChallenge(f8eEnvironment, fullAccountId.serverId, session, signature)
  }

  private suspend fun respondToAuthChallenge(
    f8eEnvironment: F8eEnvironment,
    username: String,
    session: String,
    signature: String,
  ): Result<AccountAuthTokens, AuthError> {
    return authenticationService
      .completeAuthentication(f8eEnvironment, username, signature, session)
      .mapError { AuthNetworkError("Cannot complete authentication: ${it.message}") }
  }
}
