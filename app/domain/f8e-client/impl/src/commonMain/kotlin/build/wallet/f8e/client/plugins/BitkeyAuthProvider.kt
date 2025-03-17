package build.wallet.f8e.client.plugins

import build.wallet.auth.AuthTokensService
import build.wallet.logging.LogLevel
import build.wallet.logging.logDev
import com.github.michaelbull.result.get
import io.ktor.client.plugins.auth.AuthProvider
import io.ktor.client.plugins.auth.providers.BearerTokens
import io.ktor.client.plugins.auth.providers.RefreshTokensParams
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.headers
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.request
import io.ktor.http.HttpHeaders
import io.ktor.http.auth.AuthScheme
import io.ktor.http.auth.HttpAuthHeader
import io.ktor.util.Attributes

/**
 * A simplified implementation of [io.ktor.client.plugins.auth.providers.BearerAuthProvider]
 * that provides the request attributes to the `loadTokens` method.
 */
internal class BitkeyAuthProvider(
  private val authTokensService: AuthTokensService,
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
          BearerTokens(
            accessToken = tokens.accessToken.raw,
            refreshToken = tokens.refreshToken.raw
          )
        }
    }

  private val refreshTokens: suspend RefreshTokensParams.() -> BearerTokens? =
    refreshTokens@{
      val attributes = response.call.attributes
      val f8eEnvironment = attributes[F8eEnvironmentAttribute]
      val accountId = attributes.getOrNull(AccountIdAttribute) ?: return@refreshTokens null
      val authTokenScope = requireNotNull(attributes.getOrNull(AuthTokenScopeAttribute)) {
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
      RefreshTokensParams(
        client = response.call.client,
        response = response,
        oldTokens = loadTokens(response.request.attributes)
      )
    )
    return newToken != null
  }
}
