package build.wallet.f8e.auth

import build.wallet.auth.AccountAuthTokens
import build.wallet.auth.RefreshToken
import build.wallet.bitkey.auth.AuthPublicKey
import build.wallet.f8e.F8eEnvironment
import build.wallet.ktor.result.NetworkingError
import com.github.michaelbull.result.Result
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

interface AuthenticationService {
  suspend fun initiateAuthentication(
    f8eEnvironment: F8eEnvironment,
    authPublicKey: AuthPublicKey,
  ): Result<InitiateAuthenticationSuccess, NetworkingError>

  @Serializable
  data class InitiateAuthenticationSuccess(
    val username: String,
    @SerialName("account_id")
    val accountId: String,
    val challenge: String,
    val session: String,
  )

  suspend fun completeAuthentication(
    f8eEnvironment: F8eEnvironment,
    username: String,
    challengeResponse: String,
    session: String,
  ): Result<AccountAuthTokens, NetworkingError>

  suspend fun refreshToken(
    f8eEnvironment: F8eEnvironment,
    refreshToken: RefreshToken,
  ): Result<AccountAuthTokens, NetworkingError>
}
