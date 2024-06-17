package build.wallet.auth

import build.wallet.account.AccountRepositoryFake
import build.wallet.account.AccountStatus
import build.wallet.auth.LiteAccountCreationError.LiteAccountCreationAuthError
import build.wallet.auth.LiteAccountCreationError.LiteAccountCreationF8eError
import build.wallet.auth.LiteAccountCreationError.LiteAccountKeyGenerationError
import build.wallet.bitkey.account.LiteAccount
import build.wallet.bitkey.auth.AppRecoveryAuthPublicKeyMock
import build.wallet.bitkey.keybox.LiteAccountConfigMock
import build.wallet.coroutines.turbine.turbines
import build.wallet.f8e.error.F8eError
import build.wallet.f8e.onboarding.CreateLiteAccountF8eClientMock
import build.wallet.keybox.keys.AppKeysGeneratorMock
import build.wallet.testing.shouldBeErrOfType
import build.wallet.testing.shouldBeOk
import com.github.michaelbull.result.Err
import com.github.michaelbull.result.get
import com.github.michaelbull.result.unwrap
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeTypeOf

class LiteAccountCreatorImplTests : FunSpec({

  val accountAuthorizer = AccountAuthenticatorMock(turbines::create)
  val accountRepository = AccountRepositoryFake()
  val authTokenDao = AuthTokenDaoMock(turbines::create)
  val appKeysGenerator = AppKeysGeneratorMock()
  val createLiteAccountF8eClient = CreateLiteAccountF8eClientMock(turbines::create)

  val creator =
    LiteAccountCreatorImpl(
      accountAuthenticator = accountAuthorizer,
      accountRepository = accountRepository,
      authTokenDao = authTokenDao,
      appKeysGenerator = appKeysGenerator,
      createLiteAccountF8eClient = createLiteAccountF8eClient
    )

  beforeTest {
    accountAuthorizer.reset()
    accountRepository.reset()
    appKeysGenerator.reset()
    createLiteAccountF8eClient.reset()
  }

  test("Happy path") {
    accountRepository.accountState.value.get().shouldBe(AccountStatus.NoAccount)
    val accountId = createLiteAccountF8eClient.createResult.unwrap()
    val recoveryKey = appKeysGenerator.recoveryAuthKeyResult.unwrap()
    val tokens = accountAuthorizer.authResults.first().unwrap().authTokens

    val account =
      creator.createAccount(LiteAccountConfigMock)
        .shouldBeOk(
          LiteAccount(accountId, LiteAccountConfigMock, AppRecoveryAuthPublicKeyMock)
        )

    createLiteAccountF8eClient.createCalls.awaitItem()
      .shouldBe(recoveryKey)

    accountAuthorizer.authCalls.awaitItem()
      .shouldBe(recoveryKey)

    authTokenDao.setTokensCalls.awaitItem()
      .shouldBeTypeOf<AuthTokenDaoMock.SetTokensParams>()
      .tokens.shouldBe(tokens)

    accountRepository.accountState.value.get().shouldBe(AccountStatus.OnboardingAccount(account))
  }

  test("AppKeysGenerator failure binds") {
    appKeysGenerator.recoveryAuthKeyResult = Err(Throwable())
    creator.createAccount(LiteAccountConfigMock)
      .shouldBeErrOfType<LiteAccountKeyGenerationError>()
  }

  test("CreateLiteAccountAndKeysF8eClient failure binds") {
    createLiteAccountF8eClient.createResult = Err(F8eError.UnhandledError(Error()))
    creator.createAccount(LiteAccountConfigMock)
      .shouldBeErrOfType<LiteAccountCreationF8eError>()

    createLiteAccountF8eClient.createCalls.awaitItem()
  }

  test("AccountAuthorizer failure binds") {
    accountAuthorizer.authResults = mutableListOf(Err(UnhandledError(Throwable())))
    creator.createAccount(LiteAccountConfigMock)
      .shouldBeErrOfType<LiteAccountCreationAuthError>()

    createLiteAccountF8eClient.createCalls.awaitItem()
    accountAuthorizer.authCalls.awaitItem()
  }
})
