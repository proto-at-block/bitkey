package build.wallet.f8e.client.plugins

import bitkey.auth.AccessToken
import bitkey.auth.AccountAuthTokens
import bitkey.auth.AuthTokenScope
import bitkey.auth.RefreshToken
import build.wallet.auth.AuthTokensServiceFake
import build.wallet.bitkey.f8e.FullAccountId
import build.wallet.f8e.F8eEnvironment
import build.wallet.ktor.test.HttpResponseMock
import build.wallet.testing.shouldBeOk
import build.wallet.time.ClockFake
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
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

class BitkeyAuthProviderTests : FunSpec({
  val authTokensService = AuthTokensServiceFake()
  val clock = ClockFake()
  val provider = BitkeyAuthProvider(authTokensService, clock)

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
      accessTokenExpiresAt = clock.now().plus(5.minutes)
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

  test("refreshes and adds new token when token is about to expire") {
    val tokens = AccountAuthTokens(
      accessToken = AccessToken("access-token"),
      refreshToken = RefreshToken("refresh-token"),
      accessTokenExpiresAt = clock.now().plus(5.seconds)
    )
    authTokensService.setTokens(
      accountId = FullAccountId("test-account"),
      tokens = tokens,
      scope = AuthTokenScope.Global
    )
    val refreshedTokens = AccountAuthTokens(
      accessToken = AccessToken("new-access-token"),
      refreshToken = RefreshToken("refresh-token"),
      accessTokenExpiresAt = clock.now().plus(5.minutes)
    )
    authTokensService.refreshTokens = refreshedTokens

    val request = createRequestBuilder()

    provider.addRequestHeaders(request, null)

    request.headers[HttpHeaders.Authorization].shouldBe("Bearer new-access-token")
    authTokensService.getTokens(
      accountId = FullAccountId("test-account"),
      scope = AuthTokenScope.Global
    ).shouldBeOk(refreshedTokens)
  }

  test("refreshes and adds new token when token is expired") {
    val tokens = AccountAuthTokens(
      accessToken = AccessToken("access-token"),
      refreshToken = RefreshToken("refresh-token"),
      accessTokenExpiresAt = clock.now().minus(1.seconds)
    )
    authTokensService.setTokens(
      accountId = FullAccountId("test-account"),
      tokens = tokens,
      scope = AuthTokenScope.Global
    )
    val refreshedTokens = AccountAuthTokens(
      accessToken = AccessToken("new-access-token"),
      refreshToken = RefreshToken("refresh-token"),
      accessTokenExpiresAt = clock.now().plus(5.minutes)
    )
    authTokensService.refreshTokens = refreshedTokens

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
      accessTokenExpiresAt = clock.now().minus(1.seconds)
    )
    authTokensService.setTokens(
      accountId = FullAccountId("test-account"),
      tokens = tokens,
      scope = AuthTokenScope.Global
    )

    val refreshedTokens = AccountAuthTokens(
      accessToken = AccessToken("new-access-token"),
      refreshToken = RefreshToken("new-refresh-token"),
      accessTokenExpiresAt = clock.now().plus(300.seconds)
    )
    authTokensService.refreshTokens = refreshedTokens

    provider.refreshToken(httpResponse).shouldBeTrue()
    authTokensService.getTokens(
      accountId = FullAccountId("test-account"),
      scope = AuthTokenScope.Global
    ).shouldBeOk(refreshedTokens)
  }

  test("refreshToken returns false when no tokens exist") {
    provider.refreshToken(httpResponse).shouldBeFalse()
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
