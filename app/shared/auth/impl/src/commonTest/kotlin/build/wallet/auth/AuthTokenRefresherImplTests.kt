package build.wallet.auth

import build.wallet.auth.AccountAuthenticator.AuthData
import build.wallet.availability.AuthSignatureStatus
import build.wallet.availability.F8eAuthSignatureStatusProviderImpl
import build.wallet.bitkey.f8e.FullAccountIdMock
import build.wallet.bitkey.keybox.KeyboxMock
import build.wallet.coroutines.turbine.turbines
import build.wallet.f8e.auth.AuthF8eClientMock
import build.wallet.ktor.result.HttpError
import build.wallet.ktor.test.HttpResponseMock
import build.wallet.testing.shouldBeErrOfType
import com.github.michaelbull.result.Err
import com.github.michaelbull.result.Ok
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeTypeOf
import io.ktor.http.HttpStatusCode

class AuthTokenRefresherImplTests : FunSpec({
  val authTokenDao = AuthTokenDaoMock(turbines::create)
  val authenticationF8eClient = AuthF8eClientMock()
  val f8eAuthSignatureStatusProvider = F8eAuthSignatureStatusProviderImpl()
  val accountAuthorizer =
    AccountAuthenticatorMock(
      turbine = turbines::create,
      authF8eClient = authenticationF8eClient
    )
  val appAuthPublicKeyProvider = AppAuthPublicKeyProviderMock()

  val refresher =
    AppAuthTokenRefresherImpl(
      authTokenDao = authTokenDao,
      accountAuthenticator = accountAuthorizer,
      authF8eClient = authenticationF8eClient,
      appAuthPublicKeyProvider = appAuthPublicKeyProvider,
      f8eAuthSignatureStatusProvider = f8eAuthSignatureStatusProvider
    )

  val f8eEnvironment = KeyboxMock.config.f8eEnvironment
  val accountId = KeyboxMock.fullAccountId

  beforeTest {
    authTokenDao.reset()
    appAuthPublicKeyProvider.reset()
  }

  test("successfully refresh access token") {
    val originalAccessToken = "original-access-token"
    val originalRefreshToken = "original-refresh-token"
    val newAccessToken = "new-access-token"

    authTokenDao.tokensFlow.value =
      AccountAuthTokens(
        accessToken = AccessToken(originalAccessToken),
        refreshToken = RefreshToken(originalRefreshToken)
      )
    authenticationF8eClient.refreshResult =
      Ok(
        AccountAuthTokens(accessToken = AccessToken(newAccessToken), refreshToken = RefreshToken(originalRefreshToken))
      )

    val newTokens =
      AccountAuthTokens(
        accessToken = AccessToken(newAccessToken),
        refreshToken = RefreshToken(originalRefreshToken)
      )
    refresher.refreshAccessTokenForAccount(
      f8eEnvironment,
      accountId,
      AuthTokenScope.Global
    ).shouldBe(Ok(newTokens))
    authTokenDao.setTokensCalls.awaitItem()
      .shouldBeTypeOf<AuthTokenDaoMock.SetTokensParams>()
      .tokens.shouldBe(newTokens)
    f8eAuthSignatureStatusProvider.authSignatureStatus().value.shouldBe(AuthSignatureStatus.Authenticated)
  }

  test("failure to read current auth tokens from storage") {
    authTokenDao.tokensFlow.value = null

    refresher.refreshAccessTokenForAccount(
      f8eEnvironment,
      accountId,
      AuthTokenScope.Global
    ).shouldBeErrOfType<AuthStorageError>()
  }

  test("authenticate with f8e from scratch after refresh failure") {
    val originalAccessToken = "original-access-token"
    val originalRefreshToken = "original-refresh-token"
    val newAccessToken = "new-access-token"

    authTokenDao.tokensFlow.value =
      AccountAuthTokens(
        accessToken = AccessToken(originalAccessToken),
        refreshToken = RefreshToken(originalRefreshToken)
      )

    authenticationF8eClient.refreshResult = Err(HttpError.ClientError(HttpResponseMock(HttpStatusCode.Unauthorized)))
    val newTokens =
      AccountAuthTokens(
        accessToken = AccessToken(newAccessToken),
        refreshToken = RefreshToken(originalRefreshToken)
      )

    val authData =
      AuthData(
        accountId = FullAccountIdMock.serverId,
        authTokens = newTokens
      )

    accountAuthorizer.authResults =
      mutableListOf(Ok(authData))

    refresher.refreshAccessTokenForAccount(
      f8eEnvironment,
      accountId,
      AuthTokenScope.Global
    ).shouldBe(Ok(newTokens))
    accountAuthorizer.authCalls.awaitItem()
    authTokenDao.setTokensCalls.awaitItem()
      .shouldBeTypeOf<AuthTokenDaoMock.SetTokensParams>()
      .tokens.shouldBe(authData.authTokens)
  }

  test("failure to get a new fresh token sets auth signature to unauthenticated") {
    val originalAccessToken = "original-access-token"
    val originalRefreshToken = "original-refresh-token"

    authenticationF8eClient.refreshResult = Err(HttpError.ClientError(HttpResponseMock(HttpStatusCode.Unauthorized)))
    accountAuthorizer.authResults =
      mutableListOf(Err(AuthSignatureMismatch))

    authTokenDao.tokensFlow.value =
      AccountAuthTokens(
        accessToken = AccessToken(originalAccessToken),
        refreshToken = RefreshToken(originalRefreshToken)
      )

    refresher.refreshAccessTokenForAccount(
      f8eEnvironment,
      accountId,
      AuthTokenScope.Global
    )

    accountAuthorizer.authCalls.awaitItem()
    f8eAuthSignatureStatusProvider.authSignatureStatus().value.shouldBe(AuthSignatureStatus.Unauthenticated)
  }
})
