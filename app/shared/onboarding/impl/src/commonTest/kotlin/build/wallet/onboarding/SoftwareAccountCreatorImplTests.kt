package build.wallet.onboarding

import build.wallet.auth.*
import build.wallet.auth.AccountAuthenticator.AuthData
import build.wallet.auth.AuthTokenDaoMock.SetTokensParams
import build.wallet.auth.AuthTokenScope.Global
import build.wallet.bitkey.account.OnboardingSoftwareAccount
import build.wallet.bitkey.auth.AppGlobalAuthPublicKeyMock
import build.wallet.bitkey.auth.AppRecoveryAuthPublicKeyMock
import build.wallet.bitkey.keybox.OnboardingSoftwareAccountConfigMock
import build.wallet.bitkey.keybox.OnboardingSoftwareAccountMock
import build.wallet.coroutines.turbine.turbines
import build.wallet.f8e.error.F8eError
import build.wallet.f8e.onboarding.CreateSoftwareAccountF8eClientMock
import build.wallet.onboarding.SoftwareAccountCreationError.SoftwareAccountCreationAuthError
import build.wallet.onboarding.SoftwareAccountCreationError.SoftwareAccountCreationDatabaseError.FailedToSaveAuthTokens
import build.wallet.onboarding.SoftwareAccountCreationError.SoftwareAccountCreationF8eError
import build.wallet.testing.shouldBeErrOfType
import build.wallet.testing.shouldBeOk
import com.github.michaelbull.result.Err
import com.github.michaelbull.result.Ok
import com.github.michaelbull.result.unwrap
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeTypeOf

class SoftwareAccountCreatorImplTests : FunSpec({

  val accountAuthenticator = AccountAuthenticatorMock(turbines::create)
  val authTokenDao = AuthTokenDaoMock(turbines::create)
  val createSoftwareAccountF8eClient = CreateSoftwareAccountF8eClientMock(turbines::create)

  val creator = SoftwareAccountCreatorImpl(
    createSoftwareAccountF8eClient = createSoftwareAccountF8eClient,
    accountAuthenticator = accountAuthenticator,
    authTokenDao = authTokenDao
  )

  val appAuthResult = Ok(
    AuthData(
      accountId = OnboardingSoftwareAccountMock.accountId.serverId,
      authTokens = AccountAuthTokens(
        accessToken = AccessToken("access-token-app"),
        refreshToken = RefreshToken("refresh-token-app")
      )
    )
  )
  val recoveryAuthResult = Ok(
    AuthData(
      accountId = OnboardingSoftwareAccountMock.accountId.serverId,
      authTokens = AccountAuthTokens(
        accessToken = AccessToken("access-token-recovery"),
        refreshToken = RefreshToken("refresh-token-recovery")
      )
    )
  )

  beforeTest {
    accountAuthenticator.reset()
    createSoftwareAccountF8eClient.reset()
  }

  test("happy path") {
    accountAuthenticator.authResults = mutableListOf(appAuthResult, recoveryAuthResult)

    val accountId = createSoftwareAccountF8eClient.createResult.unwrap()
    val appAuthTokens = appAuthResult.unwrap().authTokens
    val recoveryAuthTokens = recoveryAuthResult.unwrap().authTokens

    creator
      .createAccount(
        authKey = AppGlobalAuthPublicKeyMock,
        recoveryAuthKey = AppRecoveryAuthPublicKeyMock,
        config = OnboardingSoftwareAccountConfigMock
      )
      .shouldBeOk(
        OnboardingSoftwareAccount(
          accountId = accountId,
          config = OnboardingSoftwareAccountConfigMock,
          appGlobalAuthKey = AppGlobalAuthPublicKeyMock,
          recoveryAuthKey = AppRecoveryAuthPublicKeyMock
        )
      )

    createSoftwareAccountF8eClient.createCalls.awaitItem()
      .shouldBe(Unit)

    accountAuthenticator.authCalls.awaitItem()
      .shouldBe(AppGlobalAuthPublicKeyMock)
    authTokenDao.setTokensCalls.awaitItem()
      .shouldBeTypeOf<SetTokensParams>()
      .tokens.shouldBe(appAuthTokens)

    accountAuthenticator.authCalls.awaitItem()
      .shouldBe(AppRecoveryAuthPublicKeyMock)
    authTokenDao.setTokensCalls.awaitItem()
      .shouldBeTypeOf<SetTokensParams>()
      .tokens.shouldBe(recoveryAuthTokens)
  }

  test("createSoftwareAccountF8eClient failure binds") {
    createSoftwareAccountF8eClient.createResult = Err(F8eError.UnhandledError(Error()))
    creator.createAccount(
      authKey = AppGlobalAuthPublicKeyMock,
      recoveryAuthKey = AppRecoveryAuthPublicKeyMock,
      config = OnboardingSoftwareAccountConfigMock
    )
      .shouldBeErrOfType<SoftwareAccountCreationF8eError>()

    createSoftwareAccountF8eClient.createCalls.awaitItem()
  }

  test("accountAuthenticator app auth failure binds") {
    accountAuthenticator.authResults = mutableListOf(Err(UnhandledError(Throwable())))
    creator.createAccount(
      authKey = AppGlobalAuthPublicKeyMock,
      recoveryAuthKey = AppRecoveryAuthPublicKeyMock,
      config = OnboardingSoftwareAccountConfigMock
    )
      .shouldBeErrOfType<SoftwareAccountCreationAuthError>()

    createSoftwareAccountF8eClient.createCalls.awaitItem()
    accountAuthenticator.authCalls.awaitItem()
      .shouldBe(AppGlobalAuthPublicKeyMock)
  }

  test("accountAuthenticator recovery auth failure binds") {
    accountAuthenticator.authResults =
      mutableListOf(appAuthResult, Err(UnhandledError(Throwable())))
    creator.createAccount(
      authKey = AppGlobalAuthPublicKeyMock,
      recoveryAuthKey = AppRecoveryAuthPublicKeyMock,
      config = OnboardingSoftwareAccountConfigMock
    )
      .shouldBeErrOfType<SoftwareAccountCreationAuthError>()

    createSoftwareAccountF8eClient.createCalls.awaitItem()
    accountAuthenticator.authCalls.awaitItem()
      .shouldBe(AppGlobalAuthPublicKeyMock)
    authTokenDao.setTokensCalls.awaitItem()
      .shouldBe(
        SetTokensParams(
          accountId = OnboardingSoftwareAccountMock.accountId,
          tokens = appAuthResult.unwrap().authTokens,
          scope = Global
        )
      )

    accountAuthenticator.authCalls.awaitItem()
      .shouldBe(AppRecoveryAuthPublicKeyMock)
  }

  test("authTokenDao failure binds") {
    val tokens = accountAuthenticator.authResults.first().unwrap().authTokens

    authTokenDao.setTokensResult = Err(Throwable())
    creator.createAccount(
      authKey = AppGlobalAuthPublicKeyMock,
      recoveryAuthKey = AppRecoveryAuthPublicKeyMock,
      config = OnboardingSoftwareAccountConfigMock
    )
      .shouldBeErrOfType<FailedToSaveAuthTokens>()

    createSoftwareAccountF8eClient.createCalls.awaitItem()
    accountAuthenticator.authCalls.awaitItem()
      .shouldBe(AppGlobalAuthPublicKeyMock)
    authTokenDao.setTokensCalls.awaitItem()
      .shouldBe(
        SetTokensParams(
          accountId = OnboardingSoftwareAccountMock.accountId,
          tokens = tokens,
          scope = Global
        )
      )
  }
})
