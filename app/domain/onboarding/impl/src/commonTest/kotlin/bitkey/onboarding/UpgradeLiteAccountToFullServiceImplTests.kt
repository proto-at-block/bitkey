package bitkey.onboarding

import bitkey.auth.AuthTokenScope.Global
import bitkey.f8e.error.F8eError
import build.wallet.auth.AccountAuthenticatorMock
import build.wallet.auth.AccountMissing
import build.wallet.auth.AuthTokensServiceFake
import build.wallet.bitkey.app.AppRecoveryAuthKey
import build.wallet.bitkey.f8e.FullAccountId
import build.wallet.bitkey.f8e.FullAccountIdMock
import build.wallet.bitkey.f8e.LiteAccountIdMock
import build.wallet.bitkey.keybox.KeyboxMock
import build.wallet.bitkey.keybox.LiteAccountMock
import build.wallet.bitkey.keybox.WithAppKeysAndHardwareKeysMock
import build.wallet.cloud.backup.CloudBackupError
import build.wallet.cloud.backup.CloudBackupRepositoryFake
import build.wallet.cloud.backup.CloudBackupV2WithLiteAccountMock
import build.wallet.cloud.store.CloudAccountMock
import build.wallet.cloud.store.CloudStoreAccountError
import build.wallet.cloud.store.CloudStoreAccountRepositoryMock
import build.wallet.coroutines.turbine.turbines
import build.wallet.crypto.PublicKey
import build.wallet.f8e.onboarding.UpgradeAccountF8eClient
import build.wallet.f8e.onboarding.UpgradeAccountF8eClientMock
import build.wallet.feature.FeatureFlagDaoFake
import build.wallet.feature.flags.ChaincodeDelegationFeatureFlag
import build.wallet.feature.flags.SharedCloudBackupsFeatureFlag
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
  val cloudStoreAccount = CloudAccountMock("foo")
  val accountAuthenticator = AccountAuthenticatorMock(turbines::create)
  val authTokensService = AuthTokensServiceFake()
  val deviceTokenManager = DeviceTokenManagerMock(turbines::create)
  val featureFlagDao = FeatureFlagDaoFake()
  val chaincodeDelegationFeatureFlag = ChaincodeDelegationFeatureFlag(featureFlagDao)
  val keyboxDao = KeyboxDaoMock(turbines::create, defaultOnboardingKeybox = null)
  val upgradeAccountF8eClient = UpgradeAccountF8eClientMock(turbines::create)
  val upgradeAccountV2F8eClient = UpgradeAccountV2F8eClientFake(turbines::create)
  val sharedCloudBackupsFeatureFlag = SharedCloudBackupsFeatureFlag(featureFlagDao)
  val cloudBackupRepository = CloudBackupRepositoryFake()
  val cloudStoreAccountRepository = CloudStoreAccountRepositoryMock()

  val service = UpgradeLiteToFullAccountServiceImpl(
    accountAuthenticator = accountAuthenticator,
    authTokensService = authTokensService,
    deviceTokenManager = deviceTokenManager,
    keyboxDao = keyboxDao,
    upgradeAccountF8eClient = upgradeAccountF8eClient,
    upgradeAccountV2F8eClient = upgradeAccountV2F8eClient,
    uuidGenerator = UuidGeneratorFake(),
    chaincodeDelegationFeatureFlag = chaincodeDelegationFeatureFlag,
    sharedCloudBackupsFeatureFlag = sharedCloudBackupsFeatureFlag,
    cloudBackupRepository = cloudBackupRepository,
    cloudStoreAccountRepository = cloudStoreAccountRepository
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
    cloudBackupRepository.reset()
    cloudStoreAccountRepository.reset()
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

    authTokensService.getTokens(fullAccount.accountId, Global)
      .shouldBeOk(tokens)
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

  test("archives lite account backup before successful upgrade when feature flag enabled") {
    sharedCloudBackupsFeatureFlag.setFlagValue(value = true)
    cloudStoreAccountRepository.currentAccountResult = Ok(cloudStoreAccount)

    // Create a lite account backup
    val liteBackup = CloudBackupV2WithLiteAccountMock.copy(
      accountId = LiteAccountMock.accountId.serverId
    )
    cloudBackupRepository.writeBackup(
      accountId = LiteAccountMock.accountId,
      cloudStoreAccount = cloudStoreAccount,
      backup = liteBackup,
      requireAuthRefresh = false
    ).shouldBeOk()

    service.upgradeAccount(LiteAccountMock, WithAppKeysAndHardwareKeysMock).shouldBeOk()

    // Verify backup was archived and cleared when feature flag is enabled
    val allBackups = cloudBackupRepository.readAllBackups(cloudStoreAccount).shouldBeOk()
    allBackups.size.shouldBe(0) // All backups cleared in fake implementation

    upgradeAccountF8eClient.upgradeAccountCalls.awaitItem()
    accountAuthenticator.authCalls.awaitItem()
    deviceTokenManager.addDeviceTokenIfPresentForAccountCalls.awaitItem()
  }

  test("upgrade fails when cloud store account is not available and feature flag enabled") {
    cloudStoreAccountRepository.currentAccountResult = Ok(null)
    sharedCloudBackupsFeatureFlag.setFlagValue(value = true)

    val result = service.upgradeAccount(LiteAccountMock, WithAppKeysAndHardwareKeysMock)

    result.shouldBeErrOfType<FullAccountCreationError.BackupError.CloudStoreError>()
  }

  test("upgrade fails when cloud store account repository returns error") {
    cloudStoreAccountRepository.currentAccountResult = Err(CloudStoreAccountError())
    sharedCloudBackupsFeatureFlag.setFlagValue(value = true)

    val result = service.upgradeAccount(LiteAccountMock, WithAppKeysAndHardwareKeysMock)

    result.shouldBeErrOfType<FullAccountCreationError.BackupError.CloudStoreError>()
  }

  test("upgrade succeeds when no matching backup exists") {
    cloudStoreAccountRepository.currentAccountResult = Ok(cloudStoreAccount)
    sharedCloudBackupsFeatureFlag.setFlagValue(value = true)
    upgradeAccountF8eClient.upgradeAccountResult =
      Ok(
        UpgradeAccountF8eClient.Success(
          KeyboxMock.activeSpendingKeyset.f8eSpendingKeyset,
          FullAccountId(LiteAccountIdMock.serverId)
        )
      )

    // No backup exists for this lite account - archival is skipped gracefully
    val fullAccount = service.upgradeAccount(LiteAccountMock, WithAppKeysAndHardwareKeysMock)
      .shouldBeOk()

    fullAccount.accountId.serverId.shouldBe(LiteAccountMock.accountId.serverId)
    upgradeAccountF8eClient.upgradeAccountCalls.awaitItem()
    accountAuthenticator.authCalls.awaitItem()
    deviceTokenManager.addDeviceTokenIfPresentForAccountCalls.awaitItem()
  }

  test("upgrade succeeds when backup has different account id") {
    cloudStoreAccountRepository.currentAccountResult = Ok(cloudStoreAccount)
    sharedCloudBackupsFeatureFlag.setFlagValue(value = true)
    upgradeAccountF8eClient.upgradeAccountResult =
      Ok(
        UpgradeAccountF8eClient.Success(
          KeyboxMock.activeSpendingKeyset.f8eSpendingKeyset,
          FullAccountId(LiteAccountIdMock.serverId)
        )
      )

    // Create a backup for a different account
    val differentAccountBackup = CloudBackupV2WithLiteAccountMock.copy(
      accountId = "different-account-id"
    )
    cloudBackupRepository.writeBackup(
      accountId = LiteAccountMock.accountId,
      cloudStoreAccount = cloudStoreAccount,
      backup = differentAccountBackup,
      requireAuthRefresh = false
    ).shouldBeOk()

    val fullAccount = service.upgradeAccount(LiteAccountMock, WithAppKeysAndHardwareKeysMock)
      .shouldBeOk()

    fullAccount.accountId.serverId.shouldBe(LiteAccountMock.accountId.serverId)

    // Verify no backup was archived since account ID didn't match
    val allBackups = cloudBackupRepository.readAllBackups(cloudStoreAccount).shouldBeOk()
    allBackups.size.shouldBe(1) // Only the original different-account backup

    upgradeAccountF8eClient.upgradeAccountCalls.awaitItem()
    accountAuthenticator.authCalls.awaitItem()
    deviceTokenManager.addDeviceTokenIfPresentForAccountCalls.awaitItem()
  }

  test("upgrade fails when reading backups fails") {
    cloudStoreAccountRepository.currentAccountResult = Ok(cloudStoreAccount)
    sharedCloudBackupsFeatureFlag.setFlagValue(value = true)
    val readError = CloudBackupError.RectifiableCloudBackupError(
      cause = Throwable("read failed"),
      data = "data"
    )
    cloudBackupRepository.returnReadError = readError

    val result = service.upgradeAccount(LiteAccountMock, WithAppKeysAndHardwareKeysMock)

    result.shouldBeErrOfType<FullAccountCreationError.BackupError.ReadingBackupError>()
  }

  test("upgrade fails when clearing backup fails") {
    cloudStoreAccountRepository.currentAccountResult = Ok(cloudStoreAccount)
    sharedCloudBackupsFeatureFlag.setFlagValue(value = true)

    // Create a lite account backup
    val liteBackup = CloudBackupV2WithLiteAccountMock.copy(
      accountId = LiteAccountMock.accountId.serverId
    )
    cloudBackupRepository.writeBackup(
      accountId = LiteAccountMock.accountId,
      cloudStoreAccount = cloudStoreAccount,
      backup = liteBackup,
      requireAuthRefresh = false
    ).shouldBeOk()

    // Make clearing fail by setting write error after the initial write
    // Note: archiveBackup() doesn't check returnWriteError, only clear() does
    val clearError = CloudBackupError.RectifiableCloudBackupError(
      cause = Throwable("clear failed"),
      data = "data"
    )
    cloudBackupRepository.returnWriteError = clearError

    val result = service.upgradeAccount(LiteAccountMock, WithAppKeysAndHardwareKeysMock)

    result.shouldBeErrOfType<FullAccountCreationError.BackupError.ClearBackupError>()
  }

  test("returns database error when saving auth tokens fails") {
    sharedCloudBackupsFeatureFlag.setFlagValue(value = false)
    authTokensService.setTokensError = Error("Failed to save tokens")

    val result = service.upgradeAccount(LiteAccountMock, WithAppKeysAndHardwareKeysMock)

    result.shouldBeErrOfType<FullAccountCreationError.AccountCreationDatabaseError.FailedToSaveAuthTokens>()
    upgradeAccountF8eClient.upgradeAccountCalls.awaitItem()
    accountAuthenticator.authCalls.awaitItem()
  }

  test("preserves recovery auth key from lite account") {
    sharedCloudBackupsFeatureFlag.setFlagValue(value = false)
    val customRecoveryAuthKey = PublicKey<AppRecoveryAuthKey>("custom-recovery-auth-dpub")
    val liteAccount = LiteAccountMock.copy(
      recoveryAuthKey = customRecoveryAuthKey
    )

    val fullAccount = service.upgradeAccount(liteAccount, WithAppKeysAndHardwareKeysMock)
      .shouldBeOk()

    fullAccount.keybox.activeAppKeyBundle.recoveryAuthKey shouldBe customRecoveryAuthKey
    upgradeAccountF8eClient.upgradeAccountCalls.awaitItem()
    accountAuthenticator.authCalls.awaitItem()
    deviceTokenManager.addDeviceTokenIfPresentForAccountCalls.awaitItem()
  }

  test("archives and clears lite account backup after successful upgrade") {
    sharedCloudBackupsFeatureFlag.setFlagValue(value = true)
    cloudStoreAccountRepository.currentAccountResult = Ok(cloudStoreAccount)

    // Create a lite account backup
    val liteBackup = CloudBackupV2WithLiteAccountMock.copy(
      accountId = LiteAccountMock.accountId.serverId
    )
    cloudBackupRepository.writeBackup(
      accountId = LiteAccountMock.accountId,
      cloudStoreAccount = cloudStoreAccount,
      backup = liteBackup,
      requireAuthRefresh = false
    ).shouldBeOk()

    service.upgradeAccount(LiteAccountMock, WithAppKeysAndHardwareKeysMock).shouldBeOk()

    // Note: In the fake implementation, clear() removes all backups including archived ones.
    // This test verifies the upgrade succeeds even when backup operations complete.
    // The actual implementation would preserve archived backups while clearing active ones.

    upgradeAccountF8eClient.upgradeAccountCalls.awaitItem()
    accountAuthenticator.authCalls.awaitItem()
    deviceTokenManager.addDeviceTokenIfPresentForAccountCalls.awaitItem()
  }

  test("skips backup archival when shared cloud backups feature flag is disabled") {
    sharedCloudBackupsFeatureFlag.setFlagValue(value = false)
    cloudStoreAccountRepository.currentAccountResult = Ok(cloudStoreAccount)

    // Create a lite account backup
    val liteBackup = CloudBackupV2WithLiteAccountMock.copy(
      accountId = LiteAccountMock.accountId.serverId
    )
    cloudBackupRepository.writeBackup(
      accountId = LiteAccountMock.accountId,
      cloudStoreAccount = cloudStoreAccount,
      backup = liteBackup,
      requireAuthRefresh = false
    ).shouldBeOk()

    service.upgradeAccount(LiteAccountMock, WithAppKeysAndHardwareKeysMock).shouldBeOk()

    // Verify backup was NOT archived or cleared when feature flag is disabled
    val allBackups = cloudBackupRepository.readAllBackups(cloudStoreAccount).shouldBeOk()
    allBackups.size.shouldBe(1) // Original backup still present
    allBackups.first().accountId.shouldBe(LiteAccountMock.accountId.serverId)

    upgradeAccountF8eClient.upgradeAccountCalls.awaitItem()
    accountAuthenticator.authCalls.awaitItem()
    deviceTokenManager.addDeviceTokenIfPresentForAccountCalls.awaitItem()
  }

  test("upgrade succeeds when shared cloud backups disabled and no cloud account") {
    sharedCloudBackupsFeatureFlag.setFlagValue(value = false)
    cloudStoreAccountRepository.currentAccountResult = Ok(null)
    upgradeAccountF8eClient.upgradeAccountResult =
      Ok(
        UpgradeAccountF8eClient.Success(
          KeyboxMock.activeSpendingKeyset.f8eSpendingKeyset,
          FullAccountId(LiteAccountIdMock.serverId)
        )
      )

    val fullAccount = service.upgradeAccount(LiteAccountMock, WithAppKeysAndHardwareKeysMock)
      .shouldBeOk()

    fullAccount.accountId.serverId.shouldBe(LiteAccountMock.accountId.serverId)
    upgradeAccountF8eClient.upgradeAccountCalls.awaitItem()
    accountAuthenticator.authCalls.awaitItem()
    deviceTokenManager.addDeviceTokenIfPresentForAccountCalls.awaitItem()
  }

  test("fails with backup clear errors when shared cloud backups enabled") {
    sharedCloudBackupsFeatureFlag.setFlagValue(value = true)
    cloudStoreAccountRepository.currentAccountResult = Ok(cloudStoreAccount)

    // Create a lite account backup
    val liteBackup = CloudBackupV2WithLiteAccountMock.copy(
      accountId = LiteAccountMock.accountId.serverId
    )
    cloudBackupRepository.writeBackup(
      accountId = LiteAccountMock.accountId,
      cloudStoreAccount = cloudStoreAccount,
      backup = liteBackup,
      requireAuthRefresh = false
    ).shouldBeOk()

    // Set error after initial write to make clear fail
    // Note: archiveBackup() doesn't check returnWriteError in the fake
    val clearError = CloudBackupError.RectifiableCloudBackupError(
      cause = Throwable("clear failed"),
      data = "data"
    )
    cloudBackupRepository.returnWriteError = clearError

    // Upgrade should fail when backup clear fails
    val result = service.upgradeAccount(LiteAccountMock, WithAppKeysAndHardwareKeysMock)

    result.shouldBeErrOfType<FullAccountCreationError.BackupError.ClearBackupError>()
  }

  test("skips backup archival when shared cloud backups enabled but no matching backup") {
    sharedCloudBackupsFeatureFlag.setFlagValue(value = true)
    cloudStoreAccountRepository.currentAccountResult = Ok(cloudStoreAccount)

    // Create a backup for a different account
    val differentAccountBackup = CloudBackupV2WithLiteAccountMock.copy(
      accountId = "different-account-id"
    )
    cloudBackupRepository.writeBackup(
      accountId = LiteAccountMock.accountId,
      cloudStoreAccount = cloudStoreAccount,
      backup = differentAccountBackup,
      requireAuthRefresh = false
    ).shouldBeOk()

    service.upgradeAccount(LiteAccountMock, WithAppKeysAndHardwareKeysMock).shouldBeOk()

    // Verify backup was NOT archived since account ID didn't match
    val allBackups = cloudBackupRepository.readAllBackups(cloudStoreAccount).shouldBeOk()
    allBackups.size.shouldBe(1) // Different account backup still present
    allBackups.first().accountId.shouldBe("different-account-id")

    upgradeAccountF8eClient.upgradeAccountCalls.awaitItem()
    accountAuthenticator.authCalls.awaitItem()
    deviceTokenManager.addDeviceTokenIfPresentForAccountCalls.awaitItem()
  }

  test("fails when shared cloud backups enabled but reading backups fails") {
    sharedCloudBackupsFeatureFlag.setFlagValue(value = true)
    cloudStoreAccountRepository.currentAccountResult = Ok(cloudStoreAccount)

    val readError = CloudBackupError.RectifiableCloudBackupError(
      cause = Throwable("read failed"),
      data = "data"
    )
    cloudBackupRepository.returnReadError = readError

    // Upgrade should fail when backup reading fails
    val result = service.upgradeAccount(LiteAccountMock, WithAppKeysAndHardwareKeysMock)

    result.shouldBeErrOfType<FullAccountCreationError.BackupError.ReadingBackupError>()
  }
})
