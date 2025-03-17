package bitkey.onboarding

import bitkey.auth.AuthTokenScope.Recovery
import bitkey.f8e.error.F8eError
import bitkey.onboarding.LiteAccountCreationError.*
import build.wallet.account.AccountServiceFake
import build.wallet.account.AccountStatus
import build.wallet.auth.AccountAuthenticatorMock
import build.wallet.auth.AuthTokensServiceFake
import build.wallet.auth.UnhandledError
import build.wallet.bitkey.account.LiteAccount
import build.wallet.bitkey.auth.AppRecoveryAuthPublicKeyMock
import build.wallet.bitkey.f8e.LiteAccountId
import build.wallet.bitkey.keybox.LiteAccountConfigMock
import build.wallet.coroutines.turbine.turbines
import build.wallet.f8e.onboarding.CreateLiteAccountF8eClientMock
import build.wallet.keybox.keys.AppKeysGeneratorMock
import build.wallet.testing.shouldBeErrOfType
import build.wallet.testing.shouldBeOk
import com.github.michaelbull.result.Err
import com.github.michaelbull.result.get
import com.github.michaelbull.result.unwrap
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

class CreateLiteAccountServiceImplTests : FunSpec({

  val accountAuthorizer = AccountAuthenticatorMock(turbines::create)
  val accountService = AccountServiceFake()
  val authTokensService = AuthTokensServiceFake()
  val appKeysGenerator = AppKeysGeneratorMock()
  val createLiteAccountF8eClient = CreateLiteAccountF8eClientMock(turbines::create)

  val service = CreateLiteAccountServiceImpl(
    accountAuthenticator = accountAuthorizer,
    accountService = accountService,
    authTokensService = authTokensService,
    appKeysGenerator = appKeysGenerator,
    createLiteAccountF8eClient = createLiteAccountF8eClient
  )

  beforeTest {
    accountAuthorizer.reset()
    accountService.reset()
    appKeysGenerator.reset()
    createLiteAccountF8eClient.reset()
    authTokensService.reset()
  }

  test("Happy path") {
    accountService.accountState.value.get().shouldBe(AccountStatus.NoAccount)
    val accountId = createLiteAccountF8eClient.createResult.unwrap()
    val recoveryKey = appKeysGenerator.recoveryAuthKeyResult.unwrap()
    val tokens = accountAuthorizer.authResults.first().unwrap().authTokens

    val account =
      service.createAccount(LiteAccountConfigMock)
        .shouldBeOk(
          LiteAccount(accountId, LiteAccountConfigMock, AppRecoveryAuthPublicKeyMock)
        )

    createLiteAccountF8eClient.createCalls.awaitItem()
      .shouldBe(recoveryKey)

    accountAuthorizer.authCalls.awaitItem()
      .shouldBe(recoveryKey)

    authTokensService.getTokens(LiteAccountId("account-id-fake"), Recovery).shouldBeOk(tokens)
    accountService.accountState.value.get().shouldBe(AccountStatus.OnboardingAccount(account))
  }

  test("AppKeysGenerator failure binds") {
    appKeysGenerator.recoveryAuthKeyResult = Err(Throwable())
    service.createAccount(LiteAccountConfigMock)
      .shouldBeErrOfType<LiteAccountKeyGenerationError>()
  }

  test("CreateLiteAccountAndKeysF8eClient failure binds") {
    createLiteAccountF8eClient.createResult = Err(F8eError.UnhandledError(Error()))
    service.createAccount(LiteAccountConfigMock)
      .shouldBeErrOfType<LiteAccountCreationF8eError>()

    createLiteAccountF8eClient.createCalls.awaitItem()
  }

  test("AccountAuthorizer failure binds") {
    accountAuthorizer.authResults = mutableListOf(Err(UnhandledError(Throwable())))
    service.createAccount(LiteAccountConfigMock)
      .shouldBeErrOfType<LiteAccountCreationAuthError>()

    createLiteAccountF8eClient.createCalls.awaitItem()
    accountAuthorizer.authCalls.awaitItem()
  }
})
