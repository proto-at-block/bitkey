package build.wallet.auth

import build.wallet.bitkey.f8e.FullAccountId
import build.wallet.bitkey.keybox.KeyboxMock
import build.wallet.bitkey.keybox.LiteAccountMock
import build.wallet.bitkey.keybox.WithAppKeysAndHardwareKeysMock
import build.wallet.coroutines.turbine.turbines
import build.wallet.crypto.PublicKey
import build.wallet.f8e.error.F8eError
import build.wallet.f8e.onboarding.UpgradeAccountService
import build.wallet.f8e.onboarding.UpgradeAccountServiceMock
import build.wallet.keybox.KeyboxDaoMock
import build.wallet.notifications.DeviceTokenManagerError
import build.wallet.notifications.DeviceTokenManagerMock
import build.wallet.notifications.DeviceTokenManagerResult
import build.wallet.platform.random.UuidGeneratorFake
import build.wallet.testing.shouldBeErrOfType
import build.wallet.testing.shouldBeLoaded
import build.wallet.testing.shouldBeOk
import com.github.michaelbull.result.Err
import com.github.michaelbull.result.Ok
import com.github.michaelbull.result.unwrap
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeTypeOf

class LiteToFullAccountUpgraderImplTests : FunSpec({
  val accountAuthorizer = AccountAuthenticatorMock(turbines::create)
  val authTokenDao = AuthTokenDaoMock(turbines::create)
  val deviceTokenManager = DeviceTokenManagerMock(turbines::create)
  val keyboxDao = KeyboxDaoMock(turbines::create, defaultOnboardingKeybox = null)
  val upgradeAccountService = UpgradeAccountServiceMock(turbines::create)

  val upgrader =
    LiteToFullAccountUpgraderImpl(
      accountAuthenticator = accountAuthorizer,
      authTokenDao = authTokenDao,
      deviceTokenManager = deviceTokenManager,
      keyboxDao = keyboxDao,
      upgradeAccountService = upgradeAccountService,
      uuidGenerator = UuidGeneratorFake()
    )

  beforeTest {
    accountAuthorizer.reset()
    authTokenDao.reset()
    deviceTokenManager.reset()
    keyboxDao.reset()
    upgradeAccountService.reset()
  }

  test("Happy path") {
    keyboxDao.onboardingKeybox.value.unwrap().shouldNotBeNull().shouldBeLoaded(null)

    val liteAccount = LiteAccountMock.copy(
      recoveryAuthKey = PublicKey("other-app-recovery-auth-dpub")
    )
    upgradeAccountService.upgradeAccountResult =
      Ok(
        UpgradeAccountService.Success(
          KeyboxMock.activeSpendingKeyset.f8eSpendingKeyset,
          FullAccountId(liteAccount.accountId.serverId)
        )
      )
    val keys = WithAppKeysAndHardwareKeysMock.copy(config = KeyboxMock.config)
    val tokens = accountAuthorizer.authResults.first().unwrap().authTokens

    val fullAccount = upgrader.upgradeAccount(liteAccount, keys).shouldBeOk()
    fullAccount.accountId.serverId.shouldBe(liteAccount.accountId.serverId)
    fullAccount.keybox.activeAppKeyBundle.recoveryAuthKey.shouldBe(liteAccount.recoveryAuthKey)

    upgradeAccountService.upgradeAccountCalls.awaitItem()

    accountAuthorizer.authCalls.awaitItem()
      .shouldBe(keys.appKeyBundle.authKey)

    authTokenDao.setTokensCalls.awaitItem()
      .shouldBeTypeOf<AuthTokenDaoMock.SetTokensParams>()
      .tokens.shouldBe(tokens)

    deviceTokenManager.addDeviceTokenIfPresentForAccountCalls.awaitItem()
    keyboxDao.onboardingKeybox.value.unwrap().shouldNotBeNull().shouldBeLoaded(fullAccount.keybox)
  }

  test("UpgradeAccountService failure binds") {
    upgradeAccountService.upgradeAccountResult = Err(F8eError.UnhandledError(Error()))
    upgrader.upgradeAccount(LiteAccountMock, WithAppKeysAndHardwareKeysMock)
      .shouldBeErrOfType<AccountCreationError.AccountCreationF8eError>()

    upgradeAccountService.upgradeAccountCalls.awaitItem()
  }

  test("AccountAuthenticator failure binds") {
    accountAuthorizer.authResults = mutableListOf(Err(AccountMissing))
    upgrader.upgradeAccount(LiteAccountMock, WithAppKeysAndHardwareKeysMock)
      .shouldBeErrOfType<AccountCreationError.AccountCreationAuthError>()

    upgradeAccountService.upgradeAccountCalls.awaitItem()
    accountAuthorizer.authCalls.awaitItem()
  }

  test("DeviceTokenManager failure does not bind") {
    deviceTokenManager.addDeviceTokenIfPresentForAccountReturn =
      DeviceTokenManagerResult.Err(
        DeviceTokenManagerError.NoDeviceToken
      )
    upgrader.upgradeAccount(LiteAccountMock, WithAppKeysAndHardwareKeysMock)
      .shouldBeOk()

    upgradeAccountService.upgradeAccountCalls.awaitItem()
    accountAuthorizer.authCalls.awaitItem()
    authTokenDao.setTokensCalls.awaitItem()
    deviceTokenManager.addDeviceTokenIfPresentForAccountCalls.awaitItem()
  }
})
