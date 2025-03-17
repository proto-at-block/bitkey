package build.wallet.auth

import bitkey.account.AccountConfigServiceFake
import bitkey.auth.AccessToken
import bitkey.auth.AccountAuthTokens
import bitkey.auth.AuthTokenScope.Global
import bitkey.auth.RefreshToken
import build.wallet.auth.AccountAuthenticator.AuthData
import build.wallet.availability.AuthSignatureStatus
import build.wallet.availability.F8eAuthSignatureStatusProviderImpl
import build.wallet.bitkey.f8e.FullAccountIdMock
import build.wallet.bitkey.keybox.KeyboxMock
import build.wallet.coroutines.turbine.turbines
import build.wallet.f8e.auth.AuthF8eClientMock
import build.wallet.ktor.result.HttpError
import build.wallet.ktor.test.HttpResponseMock
import build.wallet.platform.config.AppVariant
import build.wallet.platform.config.AppVariant.Customer
import build.wallet.testing.shouldBeErr
import build.wallet.testing.shouldBeErrOfType
import build.wallet.testing.shouldBeOk
import com.github.michaelbull.result.Err
import com.github.michaelbull.result.Ok
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeTypeOf
import io.ktor.http.*

class AuthTokensServiceImplTests : FunSpec({
  val authTokenDao = AuthTokenDaoMock(turbines::create)
  val authenticationF8eClient = AuthF8eClientMock()
  val f8eAuthSignatureStatusProvider = F8eAuthSignatureStatusProviderImpl()
  val accountConfigService = AccountConfigServiceFake()
  val accountAuthorizer = AccountAuthenticatorMock(
    turbine = turbines::create
  )
  val appAuthPublicKeyProvider = AppAuthPublicKeyProviderMock()

  val service = AuthTokensServiceImpl(
    authTokenDao = authTokenDao,
    accountAuthenticator = accountAuthorizer,
    authF8eClient = authenticationF8eClient,
    appAuthPublicKeyProvider = appAuthPublicKeyProvider,
    f8eAuthSignatureStatusProvider = f8eAuthSignatureStatusProvider,
    appVariant = Customer,
    accountConfigService = accountConfigService
  )

  val f8eEnvironment = KeyboxMock.config.f8eEnvironment
  val accountId = KeyboxMock.fullAccountId

  beforeTest {
    authTokenDao.reset()
    appAuthPublicKeyProvider.reset()
    accountConfigService.reset()
  }

  test("successfully refresh access token") {
    val originalAccessToken = "original-access-token"
    val originalRefreshToken = "original-refresh-token"
    val newAccessToken = "new-access-token"

    authTokenDao.getTokensResult = Ok(
      AccountAuthTokens(
        accessToken = AccessToken(originalAccessToken),
        refreshToken = RefreshToken(originalRefreshToken)
      )
    )
    authenticationF8eClient.refreshResult = Ok(
      AccountAuthTokens(
        accessToken = AccessToken(newAccessToken),
        refreshToken = RefreshToken(originalRefreshToken)
      )
    )

    val newTokens =
      AccountAuthTokens(
        accessToken = AccessToken(newAccessToken),
        refreshToken = RefreshToken(originalRefreshToken)
      )
    service.refreshAccessTokenWithApp(
      f8eEnvironment,
      accountId,
      Global
    ).shouldBe(Ok(newTokens))
    authTokenDao.setTokensCalls.awaitItem()
      .shouldBeTypeOf<AuthTokenDaoMock.SetTokensParams>()
      .tokens.shouldBe(newTokens)
    f8eAuthSignatureStatusProvider.authSignatureStatus().value.shouldBe(AuthSignatureStatus.Authenticated)
  }

  test("failure to read current auth tokens from storage") {
    authTokenDao.getTokensResult = Ok(null)

    service.refreshAccessTokenWithApp(
      f8eEnvironment,
      accountId,
      Global
    ).shouldBeErrOfType<AuthStorageError>()
  }

  test("authenticate with f8e from scratch after refresh failure") {
    val originalAccessToken = "original-access-token"
    val originalRefreshToken = "original-refresh-token"
    val newAccessToken = "new-access-token"

    authTokenDao.getTokensResult = Ok(
      AccountAuthTokens(
        accessToken = AccessToken(originalAccessToken),
        refreshToken = RefreshToken(originalRefreshToken)
      )
    )
    authenticationF8eClient.refreshResult =
      Err(HttpError.ClientError(HttpResponseMock(HttpStatusCode.Unauthorized)))
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

    service.refreshAccessTokenWithApp(
      f8eEnvironment,
      accountId,
      Global
    ).shouldBe(Ok(newTokens))
    accountAuthorizer.authCalls.awaitItem()
    authTokenDao.setTokensCalls.awaitItem()
      .shouldBeTypeOf<AuthTokenDaoMock.SetTokensParams>()
      .tokens.shouldBe(authData.authTokens)
  }

  test("failure to get a new fresh token sets auth signature to unauthenticated") {
    val originalAccessToken = "original-access-token"
    val originalRefreshToken = "original-refresh-token"

    authenticationF8eClient.refreshResult =
      Err(HttpError.ClientError(HttpResponseMock(HttpStatusCode.Unauthorized)))
    accountAuthorizer.authResults =
      mutableListOf(Err(AuthSignatureMismatch))

    authTokenDao.getTokensResult = Ok(
      AccountAuthTokens(
        accessToken = AccessToken(originalAccessToken),
        refreshToken = RefreshToken(originalRefreshToken)
      )
    )

    service.refreshAccessTokenWithApp(
      f8eEnvironment,
      accountId,
      Global
    )

    accountAuthorizer.authCalls.awaitItem()
    f8eAuthSignatureStatusProvider.authSignatureStatus().value.shouldBe(AuthSignatureStatus.Unauthenticated)
  }

  test("cannot clear tokens in Customer builds") {
    service.setTokens(FullAccountIdMock, AccountAuthTokensMock, Global).shouldBeOk()
    authTokenDao.setTokensCalls.awaitItem()

    service.clear().shouldBeErr(Error("Cannot clear auth tokens in production build."))

    service.getTokens(FullAccountIdMock, Global).shouldBeOk(AccountAuthTokensMock)
  }

  test("clear tokens in non Customer builds") {
    val service = AuthTokensServiceImpl(
      authTokenDao = authTokenDao,
      accountAuthenticator = accountAuthorizer,
      authF8eClient = authenticationF8eClient,
      appAuthPublicKeyProvider = appAuthPublicKeyProvider,
      f8eAuthSignatureStatusProvider = f8eAuthSignatureStatusProvider,
      appVariant = AppVariant.Team,
      accountConfigService = accountConfigService
    )

    service.setTokens(FullAccountIdMock, AccountAuthTokensMock, Global).shouldBeOk()
    authTokenDao.setTokensCalls.awaitItem()

    service.clear().shouldBeOk()
    authTokenDao.clearCalls.awaitItem()

    service.getTokens(FullAccountIdMock, Global).shouldBeOk(null)
  }
})
