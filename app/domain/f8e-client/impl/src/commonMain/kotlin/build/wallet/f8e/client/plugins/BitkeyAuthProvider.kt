package build.wallet.f8e.client.plugins

import bitkey.auth.AccountAuthTokens
import build.wallet.account.AccountService
import build.wallet.account.AccountStatus
import build.wallet.auth.AuthTokensService
import build.wallet.bitkey.f8e.AccountId
import build.wallet.logging.LogLevel
import build.wallet.logging.logDev
import build.wallet.logging.logWarn
import com.github.michaelbull.result.get
import io.ktor.client.plugins.auth.*
import io.ktor.client.plugins.auth.providers.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.http.auth.*
import io.ktor.util.*
import kotlinx.coroutines.flow.first
import kotlinx.datetime.Clock
import kotlin.time.Duration.Companion.seconds

/**
 * A simplified implementation of [io.ktor.client.plugins.auth.providers.BearerAuthProvider]
 * that integrates with our auth token storage and refresh flow.
 */
internal class BitkeyAuthProvider(
  private val authTokensService: AuthTokensService,
  private val accountService: AccountService,
  private val clock: Clock,
) : AuthProvider {
  /**
   * Determines whether tokens should be refreshed for the given account ID.
   * Returns false if the request's account ID doesn't match the current account,
   * preventing token refresh for stale account IDs.
   *
   * Scenarios where currentAccountId might be null (all allowed):
   * - Recovery flows: tokens may be refreshed before account is restored
   * - App initialization: before account data is loaded from storage
   * - Post-logout/deletion: account has been cleared but cleanup requests may still be in-flight
   * - Early onboarding: account creation is in progress
   */
  private suspend fun shouldRefreshTokensForAccount(requestAccountId: AccountId): Boolean {
    val currentAccountId = accountService.accountStatus().first()
      .get()
      ?.let { status ->
        when (status) {
          is AccountStatus.ActiveAccount -> status.account.accountId
          is AccountStatus.OnboardingAccount -> status.account.accountId
          is AccountStatus.LiteAccountUpgradingToFullAccount ->
            status.onboardingAccount.accountId
          is AccountStatus.NoAccount -> null
        }
      }

    return when {
      currentAccountId == null -> {
        // No current account - allow token refresh to proceed
        true
      }
      currentAccountId.serverId != requestAccountId.serverId -> {
        logWarn {
          "Skipping token refresh for stale account ID. " +
            "Request: ${requestAccountId.serverId}, Current: ${currentAccountId.serverId}"
        }
        false
      }
      else -> true
    }
  }

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
          if (tokens.isRefreshTokenExpired()) {
            // If the refresh token is expired (or soon to be), proactively refresh it. By doing this
            // proactively instead of relying on ktor to invoke refreshToken after a 401, we can
            // reduce 401 noise so it is easier to spot real refresh issues.
            refreshRefreshToken(attributes)
          } else if (tokens.isAccessTokenExpired()) {
            // If the access token is expired (or soon to be), preemptively refresh it.
            refreshAccessToken(attributes)
          } else {
            BearerTokens(
              accessToken = tokens.accessToken.raw,
              refreshToken = tokens.refreshToken.raw
            )
          }
        }
    }

  private val refreshAccessToken: suspend Attributes.() -> BearerTokens? =
    refreshAccessToken@{
      val f8eEnvironment = this[F8eEnvironmentAttribute]
      val accountId = this.getOrNull(AccountIdAttribute) ?: return@refreshAccessToken null
      val authTokenScope = requireNotNull(this.getOrNull(AuthTokenScopeAttribute)) {
        "Missing default `AuthTokenScopeAttribute` or `withAccountId(.., authTokenScope)`"
      }

      // Validate that the request's account ID matches the current account.
      // If validation fails (stale account), return null to gracefully fail the request.
      // Ktor will treat this as an authentication failure and return a 401-like error to the caller.
      if (!shouldRefreshTokensForAccount(accountId)) {
        return@refreshAccessToken null
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

  private val refreshRefreshToken: suspend Attributes.() -> BearerTokens? =
    refreshRefreshToken@{
      val f8eEnvironment = this[F8eEnvironmentAttribute]
      val accountId = this.getOrNull(AccountIdAttribute) ?: return@refreshRefreshToken null
      val authTokenScope = requireNotNull(this.getOrNull(AuthTokenScopeAttribute)) {
        "Missing default `AuthTokenScopeAttribute` or `withAccountId(.., authTokenScope)`"
      }

      // Validate that the request's account ID matches the current account.
      // If validation fails (stale account), return null to gracefully fail the request.
      // Ktor will treat this as an authentication failure and return a 401-like error to the caller.
      if (!shouldRefreshTokensForAccount(accountId)) {
        return@refreshRefreshToken null
      }

      authTokensService.refreshRefreshTokenWithApp(
        f8eEnvironment = f8eEnvironment,
        accountId = accountId,
        scope = authTokenScope
      ).get()
        ?.let { tokens ->
          BearerTokens(
            accessToken = tokens.accessToken.raw,
            refreshToken = tokens.refreshToken.raw
          )
        }
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
    val newToken = refreshAccessToken(response.call.attributes)
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
    } == true

  /**
   * Whether the refresh token should be considered expired.
   *
   * We add a 10-second jitter to help account for minor clock skew, latency, etc.
   */
  private fun AccountAuthTokens.isRefreshTokenExpired(): Boolean =
    refreshTokenExpiresAt?.let { expiresAt ->
      expiresAt <= clock.now().plus(10.seconds)
    } == true
}
