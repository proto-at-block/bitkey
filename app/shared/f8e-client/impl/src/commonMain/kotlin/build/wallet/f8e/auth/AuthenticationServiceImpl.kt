package build.wallet.f8e.auth

import build.wallet.auth.AccessToken
import build.wallet.auth.AccountAuthTokens
import build.wallet.auth.RefreshToken
import build.wallet.bitkey.app.AppGlobalAuthPublicKey
import build.wallet.bitkey.app.AppRecoveryAuthPublicKey
import build.wallet.bitkey.auth.AuthPublicKey
import build.wallet.bitkey.hardware.HwAuthPublicKey
import build.wallet.f8e.F8eEnvironment
import build.wallet.f8e.auth.AuthenticationService.InitiateAuthenticationSuccess
import build.wallet.f8e.client.UnauthenticatedF8eHttpClient
import build.wallet.ktor.result.NetworkingError
import build.wallet.ktor.result.bodyResult
import com.github.michaelbull.result.Result
import com.github.michaelbull.result.map
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

class AuthenticationServiceImpl(
  private val f8eHttpClient: UnauthenticatedF8eHttpClient, // only require unauthenticated calls
) : AuthenticationService {
  override suspend fun initiateAuthentication(
    f8eEnvironment: F8eEnvironment,
    authPublicKey: AuthPublicKey,
  ): Result<InitiateAuthenticationSuccess, NetworkingError> {
    return f8eHttpClient.unauthenticated(f8eEnvironment)
      .bodyResult<InitiateAuthenticationSuccess> {
        post("/api/authenticate") {
          setBody(AuthenticationRequest(authPublicKey))
        }
      }
  }

  @Serializable
  private data class AuthenticationRequest(
    @SerialName("auth_request_key")
    val authRequestKey: Map<String, String>,
  ) {
    constructor(authPublicKey: AuthPublicKey) : this(
      when (authPublicKey) {
        is AppGlobalAuthPublicKey -> mapOf("AppPubkey" to authPublicKey.pubKey.value)
        is AppRecoveryAuthPublicKey -> mapOf("RecoveryPubkey" to authPublicKey.pubKey.value)
        is HwAuthPublicKey -> mapOf("HwPubkey" to authPublicKey.pubKey.value)
        else -> error("Unsupported AuthPublicKey type")
      }
    )
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
    return f8eHttpClient.unauthenticated(f8eEnvironment)
      .bodyResult<AuthTokensSuccess> {
        post("/api/authenticate/tokens") {
          setBody(tokenRequest)
        }
      }.map { AccountAuthTokens(AccessToken(it.accessToken), RefreshToken(it.refreshToken)) }
  }

  override suspend fun refreshToken(
    f8eEnvironment: F8eEnvironment,
    refreshToken: RefreshToken,
  ): Result<AccountAuthTokens, NetworkingError> {
    val tokenRequest = GetTokensRequest(refreshToken = refreshToken.raw)
    return f8eHttpClient.unauthenticated(f8eEnvironment)
      .bodyResult<AuthTokensSuccess> {
        post("/api/authenticate/tokens") {
          setBody(tokenRequest)
        }
      }.map { AccountAuthTokens(AccessToken(it.accessToken), RefreshToken(it.refreshToken)) }
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
  )

  @Serializable
  private data class AuthTokensSuccess(
    @SerialName("access_token")
    val accessToken: String,
    @SerialName("refresh_token")
    val refreshToken: String,
  )
}
