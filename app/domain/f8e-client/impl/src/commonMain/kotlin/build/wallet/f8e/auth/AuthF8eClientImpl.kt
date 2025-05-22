package build.wallet.f8e.auth

import bitkey.auth.AccessToken
import bitkey.auth.AccountAuthTokens
import bitkey.auth.AuthTokenScope
import bitkey.auth.RefreshToken
import build.wallet.bitkey.app.AppAuthKey
import build.wallet.bitkey.hardware.HwAuthPublicKey
import build.wallet.crypto.PublicKey
import build.wallet.di.AppScope
import build.wallet.di.BitkeyInject
import build.wallet.f8e.F8eEnvironment
import build.wallet.f8e.auth.AuthF8eClient.InitiateAuthenticationSuccess
import build.wallet.f8e.auth.AuthF8eClient.InitiateHardwareAuthenticationSuccess
import build.wallet.f8e.client.UnauthenticatedF8eHttpClient
import build.wallet.f8e.client.plugins.withEnvironment
import build.wallet.ktor.result.*
import com.github.michaelbull.result.Result
import com.github.michaelbull.result.map
import io.ktor.client.request.*
import kotlinx.datetime.Clock
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlin.time.Duration.Companion.seconds

@BitkeyInject(AppScope::class)
class AuthF8eClientImpl(
  private val f8eHttpClient: UnauthenticatedF8eHttpClient, // only require unauthenticated calls
  private val clock: Clock,
) : AuthF8eClient {
  override suspend fun initiateAuthentication(
    f8eEnvironment: F8eEnvironment,
    authPublicKey: HwAuthPublicKey,
  ): Result<InitiateAuthenticationSuccess, NetworkingError> =
    authenticate(f8eEnvironment, AuthenticationRequest(authPublicKey))

  override suspend fun initiateAuthentication(
    f8eEnvironment: F8eEnvironment,
    authPublicKey: PublicKey<out AppAuthKey>,
    tokenScope: AuthTokenScope,
  ): Result<InitiateAuthenticationSuccess, NetworkingError> =
    authenticate(f8eEnvironment, AuthenticationRequest(authPublicKey, tokenScope))

  override suspend fun initiateHardwareAuthentication(
    f8eEnvironment: F8eEnvironment,
    authPublicKey: HwAuthPublicKey,
  ): Result<InitiateHardwareAuthenticationSuccess, NetworkingError> =
    f8eHttpClient.unauthenticated()
      .bodyResult<InitiateHardwareAuthenticationSuccess> {
        post("/api/hw-auth") {
          withEnvironment(f8eEnvironment)
          setRedactedBody(HardwareAuthenticationRequest(authPublicKey))
        }
      }

  private suspend fun authenticate(
    f8eEnvironment: F8eEnvironment,
    req: AuthenticationRequest,
  ) = f8eHttpClient.unauthenticated()
    .bodyResult<InitiateAuthenticationSuccess> {
      post("/api/authenticate") {
        withEnvironment(f8eEnvironment)
        setRedactedBody(req)
      }
    }

  @Serializable
  private data class AuthenticationRequest(
    @SerialName("auth_request_key")
    val authRequestKey: Map<String, String>,
  ) : RedactedRequestBody {
    constructor(
      authPublicKey: HwAuthPublicKey,
    ) : this(mapOf("HwPubkey" to authPublicKey.pubKey.value))

    constructor(authPublicKey: PublicKey<out AppAuthKey>, tokenScope: AuthTokenScope) : this(
      when (tokenScope) {
        AuthTokenScope.Global -> mapOf("AppPubkey" to authPublicKey.value)
        AuthTokenScope.Recovery -> mapOf("RecoveryPubkey" to authPublicKey.value)
      }
    )
  }

  @Serializable
  private data class HardwareAuthenticationRequest(
    @SerialName("hw_auth_pubkey")
    val hwAuthPubKey: String,
  ) : RedactedRequestBody {
    constructor(authPublicKey: HwAuthPublicKey) : this(authPublicKey.pubKey.value)
  }

  override suspend fun completeAuthentication(
    f8eEnvironment: F8eEnvironment,
    username: String,
    challengeResponse: String,
    session: String,
  ): Result<AccountAuthTokens, NetworkingError> {
    val tokenRequest =
      GetTokensRequest(
        challenge = ChallengeResponseParameters(username, challengeResponse, session)
      )
    return f8eHttpClient.unauthenticated()
      .bodyResult<AuthTokensSuccess> {
        post("/api/authenticate/tokens") {
          withEnvironment(f8eEnvironment)
          setRedactedBody(tokenRequest)
        }
      }.map { it.toAccountAuthTokens() }
  }

  override suspend fun refreshToken(
    f8eEnvironment: F8eEnvironment,
    refreshToken: RefreshToken,
  ): Result<AccountAuthTokens, NetworkingError> {
    val tokenRequest = GetTokensRequest(refreshToken = refreshToken.raw)
    return f8eHttpClient.unauthenticated()
      .bodyResult<AuthTokensSuccess> {
        post("/api/authenticate/tokens") {
          withEnvironment(f8eEnvironment)
          setRedactedBody(tokenRequest)
        }
      }.map { it.toAccountAuthTokens() }
  }

  @Serializable
  private data class ChallengeResponseParameters(
    val username: String,
    @SerialName("challenge_response")
    val challengeResponse: String,
    val session: String,
  )

  @Serializable
  private data class GetTokensRequest(
    @SerialName("challenge")
    val challenge: ChallengeResponseParameters? = null,
    @SerialName("refresh_token")
    val refreshToken: String? = null,
  ) : RedactedRequestBody

  @Serializable
  private data class AuthTokensSuccess(
    @SerialName("access_token")
    val accessToken: String,
    @SerialName("refresh_token")
    val refreshToken: String,
    // Seconds until the accessToken expires
    @SerialName("expires_in")
    val expiresIn: Int,
    // Seconds until the refreshToken expires
    @SerialName("refresh_token_expires_in")
    val refreshTokenExpiresIn: Int?,
  ) : RedactedResponseBody

  private fun AuthTokensSuccess.toAccountAuthTokens() =
    AccountAuthTokens(
      accessToken = AccessToken(accessToken),
      refreshToken = RefreshToken(refreshToken),
      accessTokenExpiresAt = clock.now().plus(expiresIn.seconds),
      refreshTokenExpiresAt = refreshTokenExpiresIn?.let { clock.now().plus(it.seconds) }
    )
}
