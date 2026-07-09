package build.wallet.cloud.backup.migration

import build.wallet.account.AccountServiceFake
import build.wallet.auth.AuthTokensServiceFake
import build.wallet.bitkey.keybox.FullAccountMock
import build.wallet.bitkey.keybox.LiteAccountMock
import build.wallet.bitkey.keybox.SoftwareAccountMock
import build.wallet.cloud.backup.CloudBackup
import build.wallet.cloud.backup.CloudBackupOperationLockImpl
import build.wallet.cloud.backup.CloudBackupService
import build.wallet.cloud.backup.CloudBackupServiceFake
import build.wallet.cloud.backup.CloudBackupServiceImpl
import build.wallet.cloud.backup.CloudBackupStoreImpl
import build.wallet.cloud.backup.CloudBackupStoreKeysImpl
import build.wallet.cloud.backup.CloudBackupV2
import build.wallet.cloud.backup.CloudBackupV2WithFullAccountMock
import build.wallet.cloud.backup.CloudBackupV3
import build.wallet.cloud.backup.CloudBackupV3WithFullAccountMock
import build.wallet.cloud.backup.CloudBackupV3WithLiteAccountMock
import build.wallet.cloud.backup.FullAccountCloudBackupCreatorMock
import build.wallet.cloud.backup.JsonSerializer
import build.wallet.cloud.backup.LiteAccountCloudBackupCreatorMock
import build.wallet.cloud.backup.local.CloudBackupDaoFake
import build.wallet.cloud.store.CloudAccountMock
import build.wallet.cloud.store.CloudKeyValueStoreFake
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
import build.wallet.platform.device.DeviceInfoProviderMock
import build.wallet.testing.shouldBeOk
import build.wallet.time.ClockFake
import com.github.michaelbull.result.Ok
import io.kotest.core.spec.style.FunSpec
import kotlinx.datetime.Instant
import okio.ByteString.Companion.encodeUtf8

class CloudKitBackupMigrationWorkerIntegrationTests : FunSpec({
  val featureFlagDao = FeatureFlagDaoFake()
  val featureFlag = IosCloudKitBackupFeatureFlag(featureFlagDao)
  val cloudBackupOperationLock = CloudBackupOperationLockImpl()

  val accountService = AccountServiceFake()
  val cloudStoreAccountRepository = CloudStoreAccountRepositoryMock()
  val cloudBackupService = CloudBackupServiceFake()
  val cloudBackupDao = CloudBackupDaoFake()
  val sharedCloudBackupsFeatureFlag = SharedCloudBackupsFeatureFlag(FeatureFlagDaoFake())
  val clock = ClockFake()
  val cloudBackupStoreKeys = CloudBackupStoreKeysImpl(
    sharedCloudBackupsFeatureFlag = sharedCloudBackupsFeatureFlag,
    clock = clock
  )
  val jsonSerializer = JsonSerializer()
  val cloudKitKeyValueStore = CloudKitKeyValueStoreFake()
  val ubiquitousKeyValueStore = UbiquitousKeyValueStoreFake()
  val cloudKitBackupMigrationStatusDao = CloudKitBackupMigrationStatusDaoFake()
  val fullAccountCloudBackupCreator = FullAccountCloudBackupCreatorMock(turbines::create)
  val liteAccountCloudBackupCreator = LiteAccountCloudBackupCreatorMock()

  fun newMigrationService(
    cloudBackupService: CloudBackupService,
  ) = CloudKitBackupMigrationServiceImpl(
    accountService = accountService,
    cloudStoreAccountRepository = cloudStoreAccountRepository,
    cloudBackupService = cloudBackupService,
    cloudBackupDao = cloudBackupDao,
    cloudBackupStoreKeys = cloudBackupStoreKeys,
    sharedCloudBackupsFeatureFlag = sharedCloudBackupsFeatureFlag,
    cloudKitBackupMigrationStatusDao = cloudKitBackupMigrationStatusDao,
    cloudKitKeyValueStore = cloudKitKeyValueStore,
    ubiquitousKeyValueStore = ubiquitousKeyValueStore,
    jsonSerializer = jsonSerializer,
    fullAccountCloudBackupCreator = fullAccountCloudBackupCreator,
    liteAccountCloudBackupCreator = liteAccountCloudBackupCreator
  )

  val service = newMigrationService(cloudBackupService)

  val cloudAccount = CloudAccountMock("test-cloud-instance")
  val iCloudStoreAccount = iCloudAccount(ubiquityIdentityToken = "test-ubiquity-identity-token")
  val fullAccount = FullAccountMock
  val liteAccount = LiteAccountMock

  suspend fun realICloudCloudBackupService(): CloudBackupService {
    sharedCloudBackupsFeatureFlag.setFlagValue(FeatureFlagValue.BooleanFlag(true))

    val cloudBackupStore = CloudBackupStoreImpl(
      cloudKeyValueStore = CloudKeyValueStoreFake(),
      cloudKitKeyValueStore = cloudKitKeyValueStore,
      iosCloudKitBackupFeatureFlag = featureFlag,
      jsonSerializer = jsonSerializer
    )
    return CloudBackupServiceImpl(
      cloudBackupStore = cloudBackupStore,
      cloudBackupDao = cloudBackupDao,
      authTokensService = AuthTokensServiceFake(),
      jsonSerializer = jsonSerializer,
      accountService = accountService,
      sharedCloudBackupsFeatureFlag = sharedCloudBackupsFeatureFlag,
      clock = clock,
      deviceInfoProvider = DeviceInfoProviderMock(),
      cloudBackupStoreKeys = cloudBackupStoreKeys
    )
  }

  fun worker(
    appVariant: AppVariant = AppVariant.Customer,
    cloudKitBackupMigrationService: CloudKitBackupMigrationService = service,
  ) =
    CloudKitBackupMigrationWorkerImpl(
      appVariant = appVariant,
      iosCloudKitBackupFeatureFlag = featureFlag,
      cloudBackupOperationLock = cloudBackupOperationLock,
      cloudKitBackupMigrationService = cloudKitBackupMigrationService,
      cloudKitBackupMigrationStatusDao = cloudKitBackupMigrationStatusDao
    )

  fun CloudBackup.encodeForCloudKit() =
    when (this) {
      is CloudBackupV2 -> jsonSerializer.encodeToStringResult<CloudBackupV2>(this)
      is CloudBackupV3 -> jsonSerializer.encodeToStringResult<CloudBackupV3>(this)
    }.shouldBeOk().encodeUtf8()

  beforeTest {
    featureFlagDao.reset()
    accountService.reset()
    cloudStoreAccountRepository.reset()
    cloudBackupService.reset()
    cloudBackupDao.reset()
    cloudKitKeyValueStore.reset()
    ubiquitousKeyValueStore.reset()
    cloudKitBackupMigrationStatusDao.reset()
    clock.reset()
    fullAccountCloudBackupCreator.reset()
    liteAccountCloudBackupCreator.reset()

    featureFlag.setFlagValue(FeatureFlagValue.BooleanFlag(true))
    sharedCloudBackupsFeatureFlag.setFlagValue(FeatureFlagValue.BooleanFlag(false))
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
    val iCloudCloudBackupService = realICloudCloudBackupService()
    val iCloudMigrationService = newMigrationService(iCloudCloudBackupService)

    cloudStoreAccountRepository.currentAccountResult = Ok(firstICloudAccount)
    fullAccountCloudBackupCreator.backupResult = Ok(firstBackup)
    worker(cloudKitBackupMigrationService = iCloudMigrationService).executeWork()
    fullAccountCloudBackupCreator.createCalls.awaitItem()

    cloudStoreAccountRepository.currentAccountResult = Ok(secondICloudAccount)
    fullAccountCloudBackupCreator.backupResult = Ok(secondBackup)
    worker(cloudKitBackupMigrationService = iCloudMigrationService).executeWork()
    fullAccountCloudBackupCreator.createCalls.awaitItem()

    iCloudCloudBackupService.readActiveBackup(firstICloudAccount).shouldBeOk(firstBackup)
    iCloudCloudBackupService.readActiveBackup(secondICloudAccount).shouldBeOk(secondBackup)
  }

  test("runs active reconciliation once per CloudKit flag enable cycle") {
    val firstBackup = CloudBackupV3WithFullAccountMock.copy(
      createdAt = Instant.parse("2024-01-01T00:00:00Z"),
      deviceNickname = "first-enable"
    )
    val secondBackup = CloudBackupV3WithFullAccountMock.copy(
      createdAt = Instant.parse("2024-02-01T00:00:00Z"),
      deviceNickname = "second-enable"
    )

    accountService.setActiveAccount(fullAccount)
    cloudStoreAccountRepository.currentAccountResult = Ok(iCloudStoreAccount)
    cloudBackupDao.set(fullAccount.accountId.serverId, CloudBackupV2WithFullAccountMock)
    val iCloudCloudBackupService = realICloudCloudBackupService()
    val iCloudMigrationService = newMigrationService(iCloudCloudBackupService)

    fullAccountCloudBackupCreator.backupResult = Ok(firstBackup)
    worker(cloudKitBackupMigrationService = iCloudMigrationService).executeWork()
    fullAccountCloudBackupCreator.createCalls.awaitItem()

    fullAccountCloudBackupCreator.reset()
    fullAccountCloudBackupCreator.backupResult = Ok(secondBackup)
    worker(cloudKitBackupMigrationService = iCloudMigrationService).executeWork()
    fullAccountCloudBackupCreator.createCalls.expectNoEvents()
    iCloudCloudBackupService.readActiveBackup(iCloudStoreAccount).shouldBeOk(firstBackup)

    val activeKey = "cb-${fullAccount.accountId.serverId}"
    ubiquitousKeyValueStore
      .setString(iCloudStoreAccount, activeKey, secondBackup.encodeForCloudKit().utf8())
      .shouldBeOk()

    featureFlag.setFlagValue(FeatureFlagValue.BooleanFlag(false))
    worker(cloudKitBackupMigrationService = iCloudMigrationService).executeWork()

    featureFlag.setFlagValue(FeatureFlagValue.BooleanFlag(true))
    worker(cloudKitBackupMigrationService = iCloudMigrationService).executeWork()
    fullAccountCloudBackupCreator.createCalls.expectNoEvents()
    cloudKitKeyValueStore.get(iCloudStoreAccount, activeKey).shouldBeOk(secondBackup.encodeForCloudKit())
  }

  test("revalidates stale reconciliation marker when KVS changes while flag is disabled") {
    val cloudKitBackup = CloudBackupV3WithFullAccountMock.copy(
      createdAt = Instant.parse("2024-01-01T00:00:00Z"),
      deviceNickname = "before-rollback"
    )
    val rollbackBackup = CloudBackupV3WithFullAccountMock.copy(
      createdAt = Instant.parse("2024-02-01T00:00:00Z"),
      deviceNickname = "during-rollback"
    )

    accountService.setActiveAccount(fullAccount)
    cloudStoreAccountRepository.currentAccountResult = Ok(iCloudStoreAccount)
    cloudBackupDao.set(fullAccount.accountId.serverId, CloudBackupV2WithFullAccountMock)
    val iCloudCloudBackupService = realICloudCloudBackupService()
    val iCloudMigrationService = newMigrationService(iCloudCloudBackupService)

    fullAccountCloudBackupCreator.backupResult = Ok(cloudKitBackup)
    worker(cloudKitBackupMigrationService = iCloudMigrationService).executeWork()
    fullAccountCloudBackupCreator.createCalls.awaitItem()
    iCloudCloudBackupService.readActiveBackup(iCloudStoreAccount).shouldBeOk(cloudKitBackup)

    fullAccountCloudBackupCreator.reset()
    val activeKey = "cb-${fullAccount.accountId.serverId}"
    featureFlag.setFlagValue(FeatureFlagValue.BooleanFlag(false))
    ubiquitousKeyValueStore
      .setString(iCloudStoreAccount, activeKey, rollbackBackup.encodeForCloudKit().utf8())
      .shouldBeOk()
    featureFlag.setFlagValue(FeatureFlagValue.BooleanFlag(true))

    worker(cloudKitBackupMigrationService = iCloudMigrationService).executeWork()

    fullAccountCloudBackupCreator.createCalls.expectNoEvents()
    cloudKitKeyValueStore.get(iCloudStoreAccount, activeKey).shouldBeOk(rollbackBackup.encodeForCloudKit())
  }

  test("skips unsupported account type") {
    accountService.setActiveAccount(SoftwareAccountMock)

    worker().executeWork()

    cloudBackupService.readActiveBackup(cloudAccount).shouldBeOk(null)
  }
})
