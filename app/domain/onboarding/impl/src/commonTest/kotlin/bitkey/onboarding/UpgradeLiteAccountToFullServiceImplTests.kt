package bitkey.onboarding

import bitkey.auth.AuthTokenScope.Global
import bitkey.f8e.error.F8eError
import build.wallet.auth.AccountAuthenticatorMock
import build.wallet.auth.AccountMissing
import build.wallet.auth.AuthTokensServiceFake
import build.wallet.bitkey.f8e.FullAccountId
import build.wallet.bitkey.f8e.FullAccountIdMock
import build.wallet.bitkey.keybox.KeyboxMock
import build.wallet.bitkey.keybox.LiteAccountMock
import build.wallet.bitkey.keybox.WithAppKeysAndHardwareKeysMock
import build.wallet.coroutines.turbine.turbines
import build.wallet.crypto.PublicKey
import build.wallet.f8e.onboarding.UpgradeAccountF8eClient
import build.wallet.f8e.onboarding.UpgradeAccountF8eClientMock
import build.wallet.feature.FeatureFlagDaoFake
import build.wallet.feature.flags.ChaincodeDelegationFeatureFlag
import build.wallet.feature.setFlagValue
import build.wallet.keybox.KeyboxDaoMock
import build.wallet.notifications.DeviceTokenManagerError
import build.wallet.notifications.DeviceTokenManagerMock
import build.wallet.notifications.DeviceTokenManagerResult
import build.wallet.platform.random.UuidGeneratorFake
import build.wallet.testing.shouldBeErrOfType
import build.wallet.testing.shouldBeOk
import com.github.michaelbull.result.Err
import com.github.michaelbull.result.Ok
import com.github.michaelbull.result.unwrap
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

class UpgradeLiteToFullAccountServiceImplTests : FunSpec({
  val accountAuthenticator = AccountAuthenticatorMock(turbines::create)
  val authTokensService = AuthTokensServiceFake()
  val deviceTokenManager = DeviceTokenManagerMock(turbines::create)
  val featureFlagDao = FeatureFlagDaoFake()
  val chaincodeDelegationFeatureFlag = ChaincodeDelegationFeatureFlag(featureFlagDao)
  val keyboxDao = KeyboxDaoMock(turbines::create, defaultOnboardingKeybox = null)
  val upgradeAccountF8eClient = UpgradeAccountF8eClientMock(turbines::create)
  val upgradeAccountV2F8eClient = UpgradeAccountV2F8eClientFake(turbines::create)

  val service = UpgradeLiteToFullAccountServiceImpl(
    accountAuthenticator = accountAuthenticator,
    authTokensService = authTokensService,
    deviceTokenManager = deviceTokenManager,
    keyboxDao = keyboxDao,
    upgradeAccountF8eClient = upgradeAccountF8eClient,
    upgradeAccountV2F8eClient = upgradeAccountV2F8eClient,
    uuidGenerator = UuidGeneratorFake(),
    chaincodeDelegationFeatureFlag = chaincodeDelegationFeatureFlag
  )

  beforeTest {
    accountAuthenticator.reset()
    deviceTokenManager.reset()
    keyboxDao.reset()
    upgradeAccountF8eClient.reset()
    upgradeAccountV2F8eClient.reset()
    authTokensService.reset()
    featureFlagDao.reset()
    chaincodeDelegationFeatureFlag.setFlagValue(value = false)
  }

  test("Happy path") {
    keyboxDao.onboardingKeybox.value.shouldBeOk(null)

    val liteAccount = LiteAccountMock.copy(
      recoveryAuthKey = PublicKey("other-app-recovery-auth-dpub")
    )
    upgradeAccountF8eClient.upgradeAccountResult =
      Ok(
        UpgradeAccountF8eClient.Success(
          KeyboxMock.activeSpendingKeyset.f8eSpendingKeyset,
          FullAccountId(liteAccount.accountId.serverId)
        )
      )
    val keys = WithAppKeysAndHardwareKeysMock.copy(config = KeyboxMock.config)
    val tokens = accountAuthenticator.authResults.first().unwrap().authTokens

    val fullAccount = service.upgradeAccount(liteAccount, keys).shouldBeOk()
    fullAccount.accountId.serverId.shouldBe(liteAccount.accountId.serverId)
    fullAccount.keybox.activeAppKeyBundle.recoveryAuthKey.shouldBe(liteAccount.recoveryAuthKey)

    upgradeAccountF8eClient.upgradeAccountCalls.awaitItem()

    accountAuthenticator.authCalls.awaitItem()
      .shouldBe(keys.appKeyBundle.authKey)

    authTokensService.getTokens(FullAccountIdMock, Global).shouldBeOk(tokens)
    deviceTokenManager.addDeviceTokenIfPresentForAccountCalls.awaitItem()
    keyboxDao.onboardingKeybox.value.shouldBeOk(fullAccount.keybox)
  }

  test("UpgradeAccountF8eClient failure binds") {
    upgradeAccountF8eClient.upgradeAccountResult = Err(F8eError.UnhandledError(Error()))
    service.upgradeAccount(LiteAccountMock, WithAppKeysAndHardwareKeysMock)
      .shouldBeErrOfType<FullAccountCreationError.AccountCreationF8eError>()

    upgradeAccountF8eClient.upgradeAccountCalls.awaitItem()
  }

  test("AccountAuthenticator failure binds") {
    accountAuthenticator.authResults = mutableListOf(Err(AccountMissing))
    service.upgradeAccount(LiteAccountMock, WithAppKeysAndHardwareKeysMock)
      .shouldBeErrOfType<FullAccountCreationError.AccountCreationAuthError>()

    upgradeAccountF8eClient.upgradeAccountCalls.awaitItem()
    accountAuthenticator.authCalls.awaitItem()
  }

  test("DeviceTokenManager failure does not bind") {
    deviceTokenManager.addDeviceTokenIfPresentForAccountReturn =
      DeviceTokenManagerResult.Err(
        DeviceTokenManagerError.NoDeviceToken
      )
    val tokens = accountAuthenticator.authResults.first().unwrap().authTokens
    service.upgradeAccount(LiteAccountMock, WithAppKeysAndHardwareKeysMock)
      .shouldBeOk()

    upgradeAccountF8eClient.upgradeAccountCalls.awaitItem()
    authTokensService.getTokens(FullAccountIdMock, Global).shouldBeOk(tokens)
    accountAuthenticator.authCalls.awaitItem()
    deviceTokenManager.addDeviceTokenIfPresentForAccountCalls.awaitItem()
  }

  test("uses v1 upgrade client when chaincode delegation disabled") {
    chaincodeDelegationFeatureFlag.setFlagValue(value = false)
    upgradeAccountV2F8eClient.upgradeAccountResult = Err(F8eError.UnhandledError(Error()))

    service.upgradeAccount(LiteAccountMock, WithAppKeysAndHardwareKeysMock).shouldBeOk()

    upgradeAccountF8eClient.upgradeAccountCalls.awaitItem()
    accountAuthenticator.authCalls.awaitItem()
    deviceTokenManager.addDeviceTokenIfPresentForAccountCalls.awaitItem()
  }

  test("uses v2 upgrade client when chaincode delegation enabled") {
    chaincodeDelegationFeatureFlag.setFlagValue(value = true)
    upgradeAccountF8eClient.upgradeAccountResult = Err(F8eError.UnhandledError(Error()))

    service.upgradeAccount(LiteAccountMock, WithAppKeysAndHardwareKeysMock).shouldBeOk()

    upgradeAccountV2F8eClient.upgradeAccountCalls.awaitItem()
    accountAuthenticator.authCalls.awaitItem()
    deviceTokenManager.addDeviceTokenIfPresentForAccountCalls.awaitItem()
  }
})
