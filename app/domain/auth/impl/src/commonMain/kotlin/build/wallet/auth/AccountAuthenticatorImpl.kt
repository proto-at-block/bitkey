package build.wallet.auth

import bitkey.account.AccountConfigService
import bitkey.auth.AccountAuthTokens
import bitkey.auth.AuthTokenScope
import build.wallet.auth.AccountAuthenticator.AuthData
import build.wallet.bitkey.app.AppAuthKey
import build.wallet.bitkey.f8e.FullAccountId
import build.wallet.crypto.PublicKey
import build.wallet.di.AppScope
import build.wallet.di.BitkeyInject
import build.wallet.f8e.auth.AuthF8eClient
import build.wallet.ktor.result.HttpError
import com.github.michaelbull.result.Result
import com.github.michaelbull.result.coroutines.coroutineBinding
import com.github.michaelbull.result.mapError
import io.ktor.http.HttpStatusCode.Companion.NotFound
import okio.ByteString.Companion.encodeUtf8

@BitkeyInject(AppScope::class)
class AccountAuthenticatorImpl(
  private val appAuthKeyMessageSigner: AppAuthKeyMessageSigner,
  private val authF8eClient: AuthF8eClient,
  private val accountConfigService: AccountConfigService,
) : AccountAuthenticator {
  override suspend fun appAuth(
    appAuthPublicKey: PublicKey<out AppAuthKey>,
    authTokenScope: AuthTokenScope,
  ): Result<AuthData, AuthError> =
    coroutineBinding {
      val f8eEnvironment = accountConfigService.activeOrDefaultConfig().value.f8eEnvironment
      val signInResponse =
        authF8eClient
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
          signInResponse.username,
          signInResponse.session,
          signature
        ).bind()

      AuthData(signInResponse.accountId, authTokens)
    }

  override suspend fun hwAuth(
    fullAccountId: FullAccountId,
    session: String,
    signature: String,
  ): Result<AccountAuthTokens, AuthError> {
    return respondToAuthChallenge(fullAccountId.serverId, session, signature)
  }

  private suspend fun respondToAuthChallenge(
    username: String,
    session: String,
    signature: String,
  ): Result<AccountAuthTokens, AuthError> {
    val f8eEnvironment = accountConfigService.activeOrDefaultConfig().value.f8eEnvironment
    return authF8eClient
      .completeAuthentication(f8eEnvironment, username, signature, session)
      .mapError { AuthNetworkError("Cannot complete authentication: ${it.message}") }
  }
}
