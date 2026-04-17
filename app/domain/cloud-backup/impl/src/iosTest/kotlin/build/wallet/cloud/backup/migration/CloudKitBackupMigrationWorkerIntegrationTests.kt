package build.wallet.cloud.backup.migration

import build.wallet.account.AccountServiceFake
import build.wallet.bitkey.keybox.FullAccountMock
import build.wallet.bitkey.keybox.LiteAccountMock
import build.wallet.bitkey.keybox.SoftwareAccountMock
import build.wallet.cloud.backup.CloudBackupOperationLockImpl
import build.wallet.cloud.backup.CloudBackupServiceFake
import build.wallet.cloud.backup.CloudBackupStoreKeysImpl
import build.wallet.cloud.backup.CloudBackupV2WithFullAccountMock
import build.wallet.cloud.backup.CloudBackupV3WithFullAccountMock
import build.wallet.cloud.backup.CloudBackupV3WithLiteAccountMock
import build.wallet.cloud.backup.FullAccountCloudBackupCreatorMock
import build.wallet.cloud.backup.JsonSerializer
import build.wallet.cloud.backup.LiteAccountCloudBackupCreatorMock
import build.wallet.cloud.backup.local.CloudBackupDaoFake
import build.wallet.cloud.store.CloudAccountMock
import build.wallet.cloud.store.CloudKitKeyValueStoreFake
import build.wallet.cloud.store.CloudStoreAccountRepositoryMock
import build.wallet.cloud.store.UbiquitousKeyValueStoreFake
import build.wallet.cloud.store.iCloudAccount
import build.wallet.coroutines.turbine.turbines
import build.wallet.feature.FeatureFlagDaoFake
import build.wallet.feature.FeatureFlagValue
import build.wallet.feature.flags.IosCloudKitBackupFeatureFlag
import build.wallet.feature.flags.SharedCloudBackupsFeatureFlag
import build.wallet.platform.config.AppVariant
import build.wallet.testing.shouldBeOk
import build.wallet.time.ClockFake
import com.github.michaelbull.result.Ok
import io.kotest.core.spec.style.FunSpec
import okio.ByteString.Companion.encodeUtf8

class CloudKitBackupMigrationWorkerIntegrationTests : FunSpec({
  val featureFlagDao = FeatureFlagDaoFake()
  val featureFlag = IosCloudKitBackupFeatureFlag(featureFlagDao)
  val cloudBackupOperationLock = CloudBackupOperationLockImpl()

  val accountService = AccountServiceFake()
  val cloudStoreAccountRepository = CloudStoreAccountRepositoryMock()
  val cloudBackupService = CloudBackupServiceFake()
  val cloudBackupDao = CloudBackupDaoFake()
  val cloudBackupStoreKeys = CloudBackupStoreKeysImpl(
    sharedCloudBackupsFeatureFlag = SharedCloudBackupsFeatureFlag(FeatureFlagDaoFake()),
    clock = ClockFake()
  )
  val jsonSerializer = JsonSerializer()
  val cloudKitKeyValueStore = CloudKitKeyValueStoreFake()
  val ubiquitousKeyValueStore = UbiquitousKeyValueStoreFake()
  val fullAccountCloudBackupCreator = FullAccountCloudBackupCreatorMock(turbines::create)
  val liteAccountCloudBackupCreator = LiteAccountCloudBackupCreatorMock()

  val service = CloudKitBackupMigrationServiceImpl(
    accountService = accountService,
    cloudStoreAccountRepository = cloudStoreAccountRepository,
    cloudBackupService = cloudBackupService,
    cloudBackupDao = cloudBackupDao,
    cloudBackupStoreKeys = cloudBackupStoreKeys,
    cloudKitKeyValueStore = cloudKitKeyValueStore,
    ubiquitousKeyValueStore = ubiquitousKeyValueStore,
    jsonSerializer = jsonSerializer,
    fullAccountCloudBackupCreator = fullAccountCloudBackupCreator,
    liteAccountCloudBackupCreator = liteAccountCloudBackupCreator
  )

  val cloudAccount = CloudAccountMock("test-cloud-instance")
  val iCloudStoreAccount = iCloudAccount(ubiquityIdentityToken = "test-ubiquity-identity-token")
  val fullAccount = FullAccountMock
  val liteAccount = LiteAccountMock

  fun worker(appVariant: AppVariant = AppVariant.Customer) =
    CloudKitBackupMigrationWorkerImpl(
      appVariant = appVariant,
      iosCloudKitBackupFeatureFlag = featureFlag,
      cloudBackupOperationLock = cloudBackupOperationLock,
      cloudKitBackupMigrationService = service
    )

  beforeTest {
    featureFlagDao.reset()
    accountService.reset()
    cloudStoreAccountRepository.reset()
    cloudBackupService.reset()
    cloudBackupDao.reset()
    cloudKitKeyValueStore.reset()
    ubiquitousKeyValueStore.reset()
    fullAccountCloudBackupCreator.reset()
    liteAccountCloudBackupCreator.reset()

    featureFlag.setFlagValue(FeatureFlagValue.BooleanFlag(true))
    cloudStoreAccountRepository.currentAccountResult = Ok(cloudAccount)
  }

  test("skips when feature flag is off") {
    featureFlag.setFlagValue(FeatureFlagValue.BooleanFlag(false))
    accountService.setActiveAccount(fullAccount)
    cloudBackupDao.set(fullAccount.accountId.serverId, CloudBackupV2WithFullAccountMock)

    worker().executeWork()

    cloudBackupService.readActiveBackup(cloudAccount).shouldBeOk(null)
  }

  test("migrates archived KVS backups to CloudKit during worker execution") {
    accountService.setActiveAccount(fullAccount)
    cloudStoreAccountRepository.currentAccountResult = Ok(iCloudStoreAccount)
    val archivedKey = "cb-${fullAccount.accountId.serverId}-2024-01-01T12:00:00Z"
    ubiquitousKeyValueStore.setString(iCloudStoreAccount, archivedKey, "archived-value").shouldBeOk()

    worker().executeWork()

    cloudKitKeyValueStore.get(iCloudStoreAccount, archivedKey).shouldBeOk("archived-value".encodeUtf8())
  }

  test("does not copy non-archived KVS keys to CloudKit during worker execution") {
    accountService.setActiveAccount(fullAccount)
    cloudStoreAccountRepository.currentAccountResult = Ok(iCloudStoreAccount)
    val activeKey = "cb-${fullAccount.accountId.serverId}"
    ubiquitousKeyValueStore.setString(iCloudStoreAccount, activeKey, "active-value").shouldBeOk()

    worker().executeWork()

    cloudKitKeyValueStore.get(iCloudStoreAccount, activeKey).shouldBeOk(null)
  }

  test("skips in emergency variant") {
    accountService.setActiveAccount(fullAccount)
    cloudBackupDao.set(fullAccount.accountId.serverId, CloudBackupV2WithFullAccountMock)

    worker(appVariant = AppVariant.Emergency).executeWork()

    cloudBackupService.readActiveBackup(cloudAccount).shouldBeOk(null)
  }

  test("migrates full account when no existing cloud backup") {
    accountService.setActiveAccount(fullAccount)
    cloudBackupDao.set(fullAccount.accountId.serverId, CloudBackupV2WithFullAccountMock)
    val migratedBackup = CloudBackupV3WithFullAccountMock.copy(deviceNickname = "migrated-full")
    fullAccountCloudBackupCreator.backupResult = Ok(migratedBackup)

    worker().executeWork()
    fullAccountCloudBackupCreator.createCalls.awaitItem()

    cloudBackupService.readActiveBackup(cloudAccount).shouldBeOk(migratedBackup)
  }

  test("migrates lite account when no existing cloud backup") {
    accountService.setActiveAccount(liteAccount)
    val migratedBackup = CloudBackupV3WithLiteAccountMock.copy(deviceNickname = "migrated-lite")
    liteAccountCloudBackupCreator.createResultCreator = { Ok(migratedBackup) }

    worker().executeWork()

    cloudBackupService.readActiveBackup(cloudAccount).shouldBeOk(migratedBackup)
  }

  test("does not rewrite when existing cloud backup belongs to active account") {
    accountService.setActiveAccount(fullAccount)
    cloudBackupDao.set(fullAccount.accountId.serverId, CloudBackupV2WithFullAccountMock)

    val existingBackup = CloudBackupV3WithFullAccountMock.copy(deviceNickname = "already-in-cloud")
    cloudBackupService.writeBackup(
      accountId = fullAccount.accountId,
      cloudStoreAccount = cloudAccount,
      backup = existingBackup,
      requireAuthRefresh = false
    )
    fullAccountCloudBackupCreator.backupResult = Ok(
      CloudBackupV3WithFullAccountMock.copy(deviceNickname = "should-not-overwrite")
    )

    worker().executeWork()

    cloudBackupService.readActiveBackup(cloudAccount).shouldBeOk(existingBackup)
    fullAccountCloudBackupCreator.createCalls.expectNoEvents()
  }

  test("continues migration when existing cloud backup belongs to different account") {
    accountService.setActiveAccount(fullAccount)
    cloudBackupDao.set(fullAccount.accountId.serverId, CloudBackupV2WithFullAccountMock)
    val migratedBackup = CloudBackupV3WithFullAccountMock.copy(deviceNickname = "migrated-active")
    fullAccountCloudBackupCreator.backupResult = Ok(migratedBackup)

    val staleBackup = CloudBackupV3WithFullAccountMock.copy(
      accountId = liteAccount.accountId.serverId,
      deviceNickname = "stale-backup"
    )
    cloudBackupService.writeBackup(
      accountId = fullAccount.accountId,
      cloudStoreAccount = cloudAccount,
      backup = staleBackup,
      requireAuthRefresh = false
    )

    worker().executeWork()
    fullAccountCloudBackupCreator.createCalls.awaitItem()

    cloudBackupService.readActiveBackup(cloudAccount).shouldBeOk(migratedBackup)
  }

  test("does not overwrite mismatched backup when local full-account backup is missing") {
    accountService.setActiveAccount(fullAccount)

    val staleBackup = CloudBackupV3WithFullAccountMock.copy(
      accountId = liteAccount.accountId.serverId,
      deviceNickname = "stale-backup"
    )
    cloudBackupService.writeBackup(
      accountId = fullAccount.accountId,
      cloudStoreAccount = cloudAccount,
      backup = staleBackup,
      requireAuthRefresh = false
    )

    worker().executeWork()

    cloudBackupService.readActiveBackup(cloudAccount).shouldBeOk(staleBackup)
    fullAccountCloudBackupCreator.createCalls.expectNoEvents()
  }

  test("migrates for a new iCloud account identity with no existing backup") {
    val firstICloudAccount = iCloudAccount(ubiquityIdentityToken = "ubiquity-token-1")
    val secondICloudAccount = iCloudAccount(ubiquityIdentityToken = "ubiquity-token-2")
    val firstBackup = CloudBackupV3WithFullAccountMock.copy(deviceNickname = "first-identity")
    val secondBackup = CloudBackupV3WithFullAccountMock.copy(deviceNickname = "second-identity")

    accountService.setActiveAccount(fullAccount)
    cloudBackupDao.set(fullAccount.accountId.serverId, CloudBackupV2WithFullAccountMock)

    cloudStoreAccountRepository.currentAccountResult = Ok(firstICloudAccount)
    fullAccountCloudBackupCreator.backupResult = Ok(firstBackup)
    worker().executeWork()
    fullAccountCloudBackupCreator.createCalls.awaitItem()

    cloudStoreAccountRepository.currentAccountResult = Ok(secondICloudAccount)
    fullAccountCloudBackupCreator.backupResult = Ok(secondBackup)
    worker().executeWork()
    fullAccountCloudBackupCreator.createCalls.awaitItem()

    cloudBackupService.readActiveBackup(firstICloudAccount).shouldBeOk(firstBackup)
    cloudBackupService.readActiveBackup(secondICloudAccount).shouldBeOk(secondBackup)
  }

  test("skips unsupported account type") {
    accountService.setActiveAccount(SoftwareAccountMock)

    worker().executeWork()

    cloudBackupService.readActiveBackup(cloudAccount).shouldBeOk(null)
  }
})
