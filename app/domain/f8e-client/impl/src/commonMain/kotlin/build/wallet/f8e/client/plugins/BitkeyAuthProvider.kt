package build.wallet.f8e.client.plugins

import bitkey.auth.AccountAuthTokens
import build.wallet.auth.AuthTokensService
import build.wallet.logging.LogLevel
import build.wallet.logging.logDev
import com.github.michaelbull.result.get
import io.ktor.client.plugins.auth.*
import io.ktor.client.plugins.auth.providers.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.http.auth.*
import io.ktor.util.*
import kotlinx.datetime.Clock
import kotlin.time.Duration.Companion.seconds

/**
 * A simplified implementation of [io.ktor.client.plugins.auth.providers.BearerAuthProvider]
 * that provides the request attributes to the `loadTokens` method.
 */
internal class BitkeyAuthProvider(
  private val authTokensService: AuthTokensService,
  private val clock: Clock,
) : AuthProvider {
  private val loadTokens: suspend (Attributes) -> BearerTokens? =
    loadTokens@{ attributes: Attributes ->
      val accountId = attributes.getOrNull(AccountIdAttribute) ?: return@loadTokens null
      val authTokenScope = requireNotNull(attributes.getOrNull(AuthTokenScopeAttribute)) {
        "Missing default `AuthTokenScopeAttribute` or `withAccountId(.., authTokenScope)`"
      }

      authTokensService
        .getTokens(accountId, authTokenScope)
        .get()
        ?.let { tokens ->
          if (tokens.isAccessTokenExpired()) {
            // If the access token is expired (or soon to be), preemptively refresh it. By doing this
            // proactively instead of relying on ktor to invoke refreshToken after a 401, we can
            // reduce 401 noise so it is easier to spot real refresh issues.
            refreshTokens(attributes)
          } else {
            BearerTokens(
              accessToken = tokens.accessToken.raw,
              refreshToken = tokens.refreshToken.raw
            )
          }
        }
    }

  private val refreshTokens: suspend Attributes.() -> BearerTokens? =
    refreshTokens@{
      val f8eEnvironment = this[F8eEnvironmentAttribute]
      val accountId = this.getOrNull(AccountIdAttribute) ?: return@refreshTokens null
      val authTokenScope = requireNotNull(this.getOrNull(AuthTokenScopeAttribute)) {
        "Missing default `AuthTokenScopeAttribute` or `withAccountId(.., authTokenScope)`"
      }
      // Note: this only works for tokens generated via [AppAuthPublicKey].
      // If the tokens were generated for the request via [HwAuthPublicKey], that
      // needs more explicit handling because it requires the customer to tap with
      // their Bitkey device.
      authTokensService
        .refreshAccessTokenWithApp(f8eEnvironment, accountId, authTokenScope)
        .get()
        ?.let { tokens ->
          BearerTokens(
            accessToken = tokens.accessToken.raw,
            refreshToken = tokens.refreshToken.raw
          )
        }
    }

  @Suppress("OverridingDeprecatedMember")
  @Deprecated("Please use sendWithoutRequest function instead")
  override val sendWithoutRequest: Boolean
    get() = error("Deprecated")

  override fun sendWithoutRequest(request: HttpRequestBuilder): Boolean = true

  /**
   * Checks if current provider is applicable to the request.
   */
  override fun isApplicable(auth: HttpAuthHeader): Boolean {
    if (auth.authScheme != AuthScheme.Bearer) {
      logDev(LogLevel.Verbose) { "Bearer Auth Provider is not applicable for $auth" }
      return false
    }
    val isSameRealm = when {
      auth !is HttpAuthHeader.Parameterized -> false
      else -> true
    }
    if (!isSameRealm) {
      logDev(LogLevel.Verbose) { "Bearer Auth Provider is not applicable for this realm" }
    }
    return isSameRealm
  }

  /**
   * Adds an authentication method headers and credentials.
   */
  override suspend fun addRequestHeaders(
    request: HttpRequestBuilder,
    authHeader: HttpAuthHeader?,
  ) {
    val token = loadTokens(request.attributes) ?: return

    request.headers {
      val tokenValue = "Bearer ${token.accessToken}"
      if (contains(HttpHeaders.Authorization)) {
        remove(HttpHeaders.Authorization)
      }
      append(HttpHeaders.Authorization, tokenValue)
    }
  }

  override suspend fun refreshToken(response: HttpResponse): Boolean {
    val newToken = refreshTokens(
      response.call.attributes
    )

    return newToken != null
  }

  /**
   * Whether the access token should be considered expired.
   *
   * We add a 10-second jitter to help account for minor clock skew, latency, etc.
   */
  private fun AccountAuthTokens.isAccessTokenExpired(): Boolean =
    accessTokenExpiresAt?.let { expiresAt ->
      expiresAt <= clock.now().plus(10.seconds)
    } ?: false
}
