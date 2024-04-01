package build.wallet.auth

import build.wallet.bitkey.app.AppAuthKey
import build.wallet.bitkey.f8e.FullAccountId
import build.wallet.crypto.PublicKey
import build.wallet.f8e.F8eEnvironment
import com.github.michaelbull.result.Result

interface AccountAuthenticator {
  /**
   * Authenticate with an app authentication token.
   */
  suspend fun appAuth(
    f8eEnvironment: F8eEnvironment,
    appAuthPublicKey: PublicKey<out AppAuthKey>,
    authTokenScope: AuthTokenScope,
  ): Result<AuthData, AuthError>

  data class AuthData(
    val accountId: String,
    val authTokens: AccountAuthTokens,
  )

  /**
   * `hwAuth` expects a user's `accountId`, and both `challenge` and `session` strings. The
   * `challenge` and `session` strings can be obtained using `InitiateHardwareAuthServiceImpl`,
   * which makes a call to the server for it to initiate authentication with f8e.
   */
  suspend fun hwAuth(
    f8eEnvironment: F8eEnvironment,
    fullAccountId: FullAccountId,
    session: String,
    signature: String,
  ): Result<AccountAuthTokens, AuthError>
}
