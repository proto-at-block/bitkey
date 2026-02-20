package build.wallet.f8e.client.plugins

import bitkey.auth.AccessToken
import bitkey.auth.AccountAuthTokens
import bitkey.auth.AuthTokenScope
import bitkey.auth.RefreshToken
import build.wallet.account.AccountServiceFake
import build.wallet.account.AccountStatus
import build.wallet.auth.AuthTokensServiceFake
import build.wallet.bitkey.f8e.FullAccountId
import build.wallet.bitkey.keybox.FullAccountMock
import build.wallet.f8e.F8eEnvironment
import build.wallet.ktor.test.HttpResponseMock
import build.wallet.testing.shouldBeOk
import build.wallet.time.ClockFake
import com.github.michaelbull.result.Ok
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.booleans.shouldBeFalse
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import io.ktor.client.request.*
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.auth.*
import io.ktor.util.*
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

class BitkeyAuthProviderTests : FunSpec({
  val authTokensService = AuthTokensServiceFake()
  val accountService = AccountServiceFake()
  val clock = ClockFake()
  val provider = BitkeyAuthProvider(authTokensService, accountService, clock)

  val httpResponse = HttpResponseMock(
    status = HttpStatusCode.OK,
    callAttributes = Attributes().apply {
      put(AccountIdAttribute, FullAccountId("test-account"))
      put(AuthTokenScopeAttribute, AuthTokenScope.Global)
      put(F8eEnvironmentAttribute, F8eEnvironment.Development)
    }
  )

  beforeTest {
    authTokensService.reset()
    accountService.reset()
  }

  test("no auth header added when no tokens exist") {
    val request = createRequestBuilder()

    provider.addRequestHeaders(request, null)

    request.headers[HttpHeaders.Authorization].shouldBeNull()
  }

  test("adds auth header when tokens exist and are not expired") {
    val tokens = AccountAuthTokens(
      accessToken = AccessToken("access-token"),
      refreshToken = RefreshToken("refresh-token"),
      accessTokenExpiresAt = clock.now().plus(5.minutes),
      refreshTokenExpiresAt = clock.now().plus(5.minutes)
    )
    authTokensService.setTokens(
      accountId = FullAccountId("test-account"),
      tokens = tokens,
      scope = AuthTokenScope.Global
    )

    val request = createRequestBuilder()

    provider.addRequestHeaders(request, null)

    request.headers[HttpHeaders.Authorization].shouldBe("Bearer access-token")
  }

  test("refreshes and adds new token when access token is about to expire") {
    val tokens = AccountAuthTokens(
      accessToken = AccessToken("access-token"),
      refreshToken = RefreshToken("refresh-token"),
      accessTokenExpiresAt = clock.now().plus(5.seconds),
      refreshTokenExpiresAt = clock.now().plus(5.minutes)
    )
    authTokensService.setTokens(
      accountId = FullAccountId("test-account"),
      tokens = tokens,
      scope = AuthTokenScope.Global
    )
    val refreshedTokens = AccountAuthTokens(
      accessToken = AccessToken("new-access-token"),
      refreshToken = RefreshToken("refresh-token"),
      accessTokenExpiresAt = clock.now().plus(5.minutes),
      refreshTokenExpiresAt = clock.now().plus(5.minutes)
    )
    authTokensService.refreshAccessTokenTokens = refreshedTokens

    val request = createRequestBuilder()

    provider.addRequestHeaders(request, null)

    request.headers[HttpHeaders.Authorization].shouldBe("Bearer new-access-token")
    authTokensService.getTokens(
      accountId = FullAccountId("test-account"),
      scope = AuthTokenScope.Global
    ).shouldBeOk(refreshedTokens)
  }

  test("re-authenticates when refresh token is about to expire") {
    val tokens = AccountAuthTokens(
      accessToken = AccessToken("access-token"),
      refreshToken = RefreshToken("refresh-token"),
      accessTokenExpiresAt = clock.now().plus(5.minutes),
      refreshTokenExpiresAt = clock.now().plus(5.seconds)
    )
    authTokensService.setTokens(
      accountId = FullAccountId("test-account"),
      tokens = tokens,
      scope = AuthTokenScope.Global
    )
    val refreshedTokens = AccountAuthTokens(
      accessToken = AccessToken("new-access-token"),
      refreshToken = RefreshToken("new-refresh-token"),
      accessTokenExpiresAt = clock.now().plus(5.minutes),
      refreshTokenExpiresAt = clock.now().plus(30.days)
    )
    authTokensService.refreshRefreshTokenTokens = refreshedTokens

    val request = createRequestBuilder()

    provider.addRequestHeaders(request, null)

    request.headers[HttpHeaders.Authorization].shouldBe("Bearer new-access-token")
    authTokensService.getTokens(
      accountId = FullAccountId("test-account"),
      scope = AuthTokenScope.Global
    ).shouldBeOk(refreshedTokens)
  }

  test("isApplicable returns true for Bearer auth scheme") {
    val authHeader = HttpAuthHeader.Parameterized(
      authScheme = AuthScheme.Bearer,
      parameters = mapOf("token" to "some-token")
    )
    provider.isApplicable(authHeader).shouldBeTrue()
  }

  test("isApplicable returns false for non-Bearer auth scheme") {
    val authHeader = HttpAuthHeader.Parameterized(
      authScheme = AuthScheme.Basic,
      parameters = mapOf("token" to "some-token")
    )
    provider.isApplicable(authHeader).shouldBeFalse()
  }

  test("sendWithoutRequest always returns true") {
    val request = createRequestBuilder()
    provider.sendWithoutRequest(request).shouldBeTrue()
  }

  test("refreshToken returns true when refresh succeeds") {
    val tokens = AccountAuthTokens(
      accessToken = AccessToken("access-token"),
      refreshToken = RefreshToken("refresh-token"),
      accessTokenExpiresAt = clock.now().minus(1.seconds),
      refreshTokenExpiresAt = clock.now().plus(5.minutes)
    )
    authTokensService.setTokens(
      accountId = FullAccountId("test-account"),
      tokens = tokens,
      scope = AuthTokenScope.Global
    )

    val refreshedTokens = AccountAuthTokens(
      accessToken = AccessToken("new-access-token"),
      refreshToken = RefreshToken("new-refresh-token"),
      accessTokenExpiresAt = clock.now().plus(300.seconds),
      refreshTokenExpiresAt = clock.now().plus(5.minutes)
    )
    authTokensService.refreshAccessTokenTokens = refreshedTokens

    provider.refreshToken(httpResponse).shouldBeTrue()
    authTokensService.getTokens(
      accountId = FullAccountId("test-account"),
      scope = AuthTokenScope.Global
    ).shouldBeOk(refreshedTokens)
  }

  test("refreshToken returns false when no tokens exist") {
    provider.refreshToken(httpResponse).shouldBeFalse()
  }

  // Stale account ID validation tests

  test("token refresh blocked when request account ID doesn't match current account") {
    // Set up an active account with a different ID than the request
    val activeAccount = FullAccountMock.copy(
      accountId = FullAccountId("active-account-id")
    )
    accountService.accountState.value = Ok(AccountStatus.ActiveAccount(activeAccount))

    // Set up expiring tokens for a different (stale) account
    val staleAccountId = FullAccountId("stale-account-id")
    val tokens = AccountAuthTokens(
      accessToken = AccessToken("access-token"),
      refreshToken = RefreshToken("refresh-token"),
      accessTokenExpiresAt = clock.now().plus(5.seconds), // About to expire
      refreshTokenExpiresAt = clock.now().plus(5.minutes)
    )
    authTokensService.setTokens(
      accountId = staleAccountId,
      tokens = tokens,
      scope = AuthTokenScope.Global
    )

    // Configure refresh to return new tokens (but it should never be called)
    authTokensService.refreshAccessTokenTokens = AccountAuthTokens(
      accessToken = AccessToken("new-access-token"),
      refreshToken = RefreshToken("refresh-token"),
      accessTokenExpiresAt = clock.now().plus(5.minutes),
      refreshTokenExpiresAt = clock.now().plus(5.minutes)
    )

    val request = createRequestBuilder(staleAccountId)
    provider.addRequestHeaders(request, null)

    // Token refresh should be blocked for stale account - no auth header added
    request.headers[HttpHeaders.Authorization].shouldBeNull()
  }

  test("token refresh allowed when account IDs match") {
    // Set up an active account
    val activeAccount = FullAccountMock.copy(
      accountId = FullAccountId("active-account-id")
    )
    accountService.accountState.value = Ok(AccountStatus.ActiveAccount(activeAccount))

    // Set up expiring tokens for the same account
    val tokens = AccountAuthTokens(
      accessToken = AccessToken("access-token"),
      refreshToken = RefreshToken("refresh-token"),
      accessTokenExpiresAt = clock.now().plus(5.seconds), // About to expire
      refreshTokenExpiresAt = clock.now().plus(5.minutes)
    )
    authTokensService.setTokens(
      accountId = activeAccount.accountId,
      tokens = tokens,
      scope = AuthTokenScope.Global
    )

    // Configure refresh to return new tokens
    val refreshedTokens = AccountAuthTokens(
      accessToken = AccessToken("new-access-token"),
      refreshToken = RefreshToken("refresh-token"),
      accessTokenExpiresAt = clock.now().plus(5.minutes),
      refreshTokenExpiresAt = clock.now().plus(5.minutes)
    )
    authTokensService.refreshAccessTokenTokens = refreshedTokens

    val request = createRequestBuilder(activeAccount.accountId)
    provider.addRequestHeaders(request, null)

    // Token refresh should succeed for matching account
    request.headers[HttpHeaders.Authorization].shouldBe("Bearer new-access-token")
  }

  test("token refresh allowed when no current account (recovery flows)") {
    // No active account
    accountService.accountState.value = Ok(AccountStatus.NoAccount)

    // Set up expiring tokens for some account
    val accountId = FullAccountId("some-account-id")
    val tokens = AccountAuthTokens(
      accessToken = AccessToken("access-token"),
      refreshToken = RefreshToken("refresh-token"),
      accessTokenExpiresAt = clock.now().plus(5.seconds), // About to expire
      refreshTokenExpiresAt = clock.now().plus(5.minutes)
    )
    authTokensService.setTokens(
      accountId = accountId,
      tokens = tokens,
      scope = AuthTokenScope.Global
    )

    // Configure refresh to return new tokens
    val refreshedTokens = AccountAuthTokens(
      accessToken = AccessToken("new-access-token"),
      refreshToken = RefreshToken("refresh-token"),
      accessTokenExpiresAt = clock.now().plus(5.minutes),
      refreshTokenExpiresAt = clock.now().plus(5.minutes)
    )
    authTokensService.refreshAccessTokenTokens = refreshedTokens

    val request = createRequestBuilder(accountId)
    provider.addRequestHeaders(request, null)

    // Token refresh should succeed when no current account (recovery scenario)
    request.headers[HttpHeaders.Authorization].shouldBe("Bearer new-access-token")
  }

  test("refresh token refresh blocked when account ID doesn't match current account") {
    // Set up an active account with a different ID than the request
    val activeAccount = FullAccountMock.copy(
      accountId = FullAccountId("active-account-id")
    )
    accountService.accountState.value = Ok(AccountStatus.ActiveAccount(activeAccount))

    // Set up tokens with expiring refresh token for a different (stale) account
    val staleAccountId = FullAccountId("stale-account-id")
    val tokens = AccountAuthTokens(
      accessToken = AccessToken("access-token"),
      refreshToken = RefreshToken("refresh-token"),
      accessTokenExpiresAt = clock.now().plus(5.minutes),
      refreshTokenExpiresAt = clock.now().plus(5.seconds) // About to expire
    )
    authTokensService.setTokens(
      accountId = staleAccountId,
      tokens = tokens,
      scope = AuthTokenScope.Global
    )

    // Configure refresh to return new tokens (but it should never be called)
    authTokensService.refreshRefreshTokenTokens = AccountAuthTokens(
      accessToken = AccessToken("new-access-token"),
      refreshToken = RefreshToken("new-refresh-token"),
      accessTokenExpiresAt = clock.now().plus(5.minutes),
      refreshTokenExpiresAt = clock.now().plus(30.days)
    )

    val request = createRequestBuilder(staleAccountId)
    provider.addRequestHeaders(request, null)

    // Token refresh should be blocked for stale account - no auth header added
    request.headers[HttpHeaders.Authorization].shouldBeNull()
  }
})

private fun createRequestBuilder(
  accountId: FullAccountId = FullAccountId("test-account"),
): HttpRequestBuilder {
  return HttpRequestBuilder().apply {
    attributes.put(AccountIdAttribute, accountId)
    attributes.put(AuthTokenScopeAttribute, AuthTokenScope.Global)
    attributes.put(F8eEnvironmentAttribute, F8eEnvironment.Development)
  }
}
