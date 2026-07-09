package build.wallet.cloud.backup.migration

import build.wallet.account.AccountServiceFake
import build.wallet.auth.AuthTokensServiceFake
import build.wallet.bitkey.keybox.FullAccountMock
import build.wallet.bitkey.keybox.LiteAccountMock
import build.wallet.bitkey.keybox.SoftwareAccountMock
import build.wallet.cloud.backup.CloudBackup
import build.wallet.cloud.backup.CloudBackupError
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
import build.wallet.logging.LogLevel
import build.wallet.logging.LogWriterMock
import build.wallet.logging.Logger
import build.wallet.testing.shouldBeErrOfType
import build.wallet.platform.device.DeviceInfoProviderMock
import build.wallet.testing.shouldBeOk
import build.wallet.time.ClockFake
import co.touchlab.kermit.Severity
import com.github.michaelbull.result.Ok
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import kotlinx.datetime.Instant
import okio.ByteString.Companion.encodeUtf8

class CloudKitBackupMigrationServiceTests : FunSpec({
  val accountService = AccountServiceFake()
  val cloudStoreAccountRepository = CloudStoreAccountRepositoryMock()
  val cloudBackupService = CloudBackupServiceFake()
  val cloudBackupDao = CloudBackupDaoFake()
  val featureFlagDao = FeatureFlagDaoFake()
  val sharedCloudBackupsFeatureFlag = SharedCloudBackupsFeatureFlag(featureFlagDao)
  val clock = ClockFake()
  val cloudBackupStoreKeys = CloudBackupStoreKeysImpl(
    sharedCloudBackupsFeatureFlag = sharedCloudBackupsFeatureFlag,
    clock = clock
  )
  val jsonSerializer = JsonSerializer()
  val cloudKitKeyValueStore = CloudKitKeyValueStoreFake()
  val ubiquitousKeyValueStore = UbiquitousKeyValueStoreFake()
  val cloudKitBackupMigrationStatusDao = CloudKitBackupMigrationStatusDaoFake()
  val logWriter = LogWriterMock()
  val fullAccountCloudBackupCreator = FullAccountCloudBackupCreatorMock(turbines::create)
  val liteAccountCloudBackupCreator = LiteAccountCloudBackupCreatorMock()

  val cloudAccount = CloudAccountMock("test-cloud-instance")
  val iCloudStoreAccount = iCloudAccount(ubiquityIdentityToken = "test-ubiquity-identity-token")
  val fullAccount = FullAccountMock
  val liteAccount = LiteAccountMock

  lateinit var service: CloudKitBackupMigrationServiceImpl

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

  suspend fun realICloudCloudBackupService(
    sharedBackupsEnabled: Boolean = true,
  ): CloudBackupService {
    val iosFeatureFlagDao = FeatureFlagDaoFake()
    val iosCloudKitBackupFeatureFlag = IosCloudKitBackupFeatureFlag(iosFeatureFlagDao)
    iosCloudKitBackupFeatureFlag.setFlagValue(FeatureFlagValue.BooleanFlag(true))
    sharedCloudBackupsFeatureFlag.setFlagValue(FeatureFlagValue.BooleanFlag(sharedBackupsEnabled))

    val cloudBackupStore = CloudBackupStoreImpl(
      cloudKeyValueStore = CloudKeyValueStoreFake(),
      cloudKitKeyValueStore = cloudKitKeyValueStore,
      iosCloudKitBackupFeatureFlag = iosCloudKitBackupFeatureFlag,
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

  fun CloudBackup.encodeForCloudKit() =
    when (this) {
      is CloudBackupV2 -> jsonSerializer.encodeToStringResult<CloudBackupV2>(this)
      is CloudBackupV3 -> jsonSerializer.encodeToStringResult<CloudBackupV3>(this)
    }.shouldBeOk().encodeUtf8()

  beforeTest {
    accountService.reset()
    cloudStoreAccountRepository.reset()
    cloudBackupService.reset()
    cloudBackupDao.reset()
    cloudKitKeyValueStore.reset()
    ubiquitousKeyValueStore.reset()
    cloudKitBackupMigrationStatusDao.reset()
    featureFlagDao.reset()
    sharedCloudBackupsFeatureFlag.setFlagValue(FeatureFlagValue.BooleanFlag(false))
    clock.reset()
    logWriter.clear()
    fullAccountCloudBackupCreator.reset()
    liteAccountCloudBackupCreator.reset()
    cloudStoreAccountRepository.currentAccountResult = Ok(cloudAccount)
    Logger.configure(
      tag = "CloudKitBackupMigrationServiceTests",
      minimumLogLevel = LogLevel.Verbose,
      logWriters = listOf(logWriter)
    )

    service = newMigrationService(cloudBackupService)
  }

  test("skips when no active account") {
    service.migrateIfNeeded().shouldBeOk()
    cloudBackupService.readActiveBackup(cloudAccount).shouldBeOk(null)
  }

  test("skips when no cloud store account") {
    accountService.setActiveAccount(fullAccount)
    cloudStoreAccountRepository.currentAccountResult = Ok(null)

    service.migrateIfNeeded().shouldBeOk()
    cloudBackupService.readActiveBackup(cloudAccount).shouldBeOk(null)
  }

  test("skips write when existing cloud backup belongs to active account") {
    accountService.setActiveAccount(fullAccount)
    cloudBackupDao.set(fullAccount.accountId.serverId, CloudBackupV2WithFullAccountMock)

    val existingBackup = CloudBackupV3WithFullAccountMock.copy(
      deviceNickname = "already-in-cloud"
    )
    cloudBackupService.writeBackup(
      accountId = fullAccount.accountId,
      cloudStoreAccount = cloudAccount,
      backup = existingBackup,
      requireAuthRefresh = false
    )

    fullAccountCloudBackupCreator.backupResult = Ok(
      CloudBackupV3WithFullAccountMock.copy(deviceNickname = "should-not-overwrite")
    )

    service.migrateIfNeeded().shouldBeOk()

    cloudBackupService.readActiveBackup(cloudAccount).shouldBeOk(existingBackup)
    fullAccountCloudBackupCreator.createCalls.expectNoEvents()
  }

  test("writes full account backup to CloudKit when no existing backup") {
    accountService.setActiveAccount(fullAccount)
    cloudBackupDao.set(fullAccount.accountId.serverId, CloudBackupV2WithFullAccountMock)
    val migratedBackup = CloudBackupV3WithFullAccountMock.copy(deviceNickname = "migrated-full")
    fullAccountCloudBackupCreator.backupResult = Ok(migratedBackup)

    service.migrateIfNeeded().shouldBeOk()
    fullAccountCloudBackupCreator.createCalls.awaitItem()

    cloudBackupService.readActiveBackup(cloudAccount).shouldBeOk(migratedBackup)
    logWriter.logs.any {
      it.severity == Severity.Info &&
        it.message == "CloudKit active backup migration started for account [${fullAccount.accountId.serverId}]"
    }.shouldBe(true)
    logWriter.logs.any {
      it.severity == Severity.Info &&
        it.message == "CloudKit active backup migration completed for account [${fullAccount.accountId.serverId}]"
    }.shouldBe(true)
  }

  test("skips full account migration when no local backup exists") {
    accountService.setActiveAccount(fullAccount)

    service.migrateIfNeeded().shouldBeOk()

    cloudBackupService.readActiveBackup(cloudAccount).shouldBeOk(null)
    fullAccountCloudBackupCreator.createCalls.expectNoEvents()
  }

  test("skips full account migration when local backup is missing required fields") {
    accountService.setActiveAccount(fullAccount)
    val noFields = CloudBackupV2WithFullAccountMock.copy(fullAccountFields = null)
    cloudBackupDao.set(fullAccount.accountId.serverId, noFields)

    service.migrateIfNeeded().shouldBeOk()

    cloudBackupService.readActiveBackup(cloudAccount).shouldBeOk(null)
    fullAccountCloudBackupCreator.createCalls.expectNoEvents()
  }

  test("writes lite account backup to CloudKit when no existing backup") {
    accountService.setActiveAccount(liteAccount)
    val migratedBackup = CloudBackupV3WithLiteAccountMock.copy(deviceNickname = "migrated-lite")
    liteAccountCloudBackupCreator.createResultCreator = { Ok(migratedBackup) }

    service.migrateIfNeeded().shouldBeOk()

    cloudBackupService.readActiveBackup(cloudAccount).shouldBeOk(migratedBackup)
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

    service.migrateIfNeeded().shouldBeOk()
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

    service.migrateIfNeeded().shouldBeOk()

    cloudBackupService.readActiveBackup(cloudAccount).shouldBeOk(staleBackup)
    fullAccountCloudBackupCreator.createCalls.expectNoEvents()
  }

  test("does not overwrite mismatched backup when upload fails") {
    accountService.setActiveAccount(fullAccount)
    cloudBackupDao.set(fullAccount.accountId.serverId, CloudBackupV2WithFullAccountMock)
    fullAccountCloudBackupCreator.backupResult = Ok(CloudBackupV3WithFullAccountMock)

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
    cloudBackupService.returnWriteError =
      CloudBackupError.UnrectifiableCloudBackupError(Error("upload failed"))

    service.migrateIfNeeded()
    fullAccountCloudBackupCreator.createCalls.awaitItem()

    cloudBackupService.readActiveBackup(cloudAccount).shouldBeOk(staleBackup)
    logWriter.logs.any {
      it.severity == Severity.Error &&
        it.message.contains("CloudKit backup migration failed")
    }.shouldBe(true)
  }

  test("skips unsupported account type") {
    accountService.setActiveAccount(SoftwareAccountMock)

    service.migrateIfNeeded().shouldBeOk()

    cloudBackupService.readActiveBackup(cloudAccount).shouldBeOk(null)
  }

  test("migrates account-specific archived KVS key to CloudKit") {
    accountService.setActiveAccount(fullAccount)
    cloudStoreAccountRepository.currentAccountResult = Ok(iCloudStoreAccount)
    val archivedKey = "cb-${fullAccount.accountId.serverId}-2024-01-01T12:00:00Z"
    ubiquitousKeyValueStore.setString(iCloudStoreAccount, archivedKey, "archived-value").shouldBeOk()

    service.migrateIfNeeded().shouldBeOk()

    cloudKitKeyValueStore.get(iCloudStoreAccount, archivedKey).shouldBeOk("archived-value".encodeUtf8())
    logWriter.logs.any {
      it.severity == Severity.Info &&
        it.message == "CloudKit archived backup migration started (keys=1)"
    }.shouldBe(true)
    logWriter.logs.any {
      it.severity == Severity.Info &&
        it.message == "CloudKit archived backup migration completed (keys=1)"
    }.shouldBe(true)
  }

  test("migrates legacy archived KVS key to CloudKit") {
    accountService.setActiveAccount(fullAccount)
    cloudStoreAccountRepository.currentAccountResult = Ok(iCloudStoreAccount)
    val archivedKey = "cloud-backup-2024-01-01T12:00:00Z"
    ubiquitousKeyValueStore.setString(iCloudStoreAccount, archivedKey, "legacy-archived-value").shouldBeOk()

    service.migrateIfNeeded().shouldBeOk()

    cloudKitKeyValueStore.get(iCloudStoreAccount, archivedKey).shouldBeOk("legacy-archived-value".encodeUtf8())
  }

  test("does not overwrite archived key that already exists in CloudKit") {
    accountService.setActiveAccount(fullAccount)
    cloudStoreAccountRepository.currentAccountResult = Ok(iCloudStoreAccount)
    val archivedKey = "cb-${fullAccount.accountId.serverId}-2024-01-01T12:00:00Z"
    ubiquitousKeyValueStore.setString(iCloudStoreAccount, archivedKey, "kvs-archived-value").shouldBeOk()
    cloudKitKeyValueStore
      .set(iCloudStoreAccount, archivedKey, "cloudkit-archived-value".encodeUtf8())
      .shouldBeOk()

    service.migrateIfNeeded().shouldBeOk()

    cloudKitKeyValueStore
      .get(iCloudStoreAccount, archivedKey)
      .shouldBeOk("cloudkit-archived-value".encodeUtf8())
  }

  test("does not copy non-archived KVS keys to CloudKit") {
    accountService.setActiveAccount(fullAccount)
    cloudStoreAccountRepository.currentAccountResult = Ok(iCloudStoreAccount)
    val activeKey = "cb-${fullAccount.accountId.serverId}"
    ubiquitousKeyValueStore.setString(iCloudStoreAccount, activeKey, "active-value").shouldBeOk()

    service.migrateIfNeeded().shouldBeOk()

    cloudKitKeyValueStore.get(iCloudStoreAccount, activeKey).shouldBeOk(null)
  }

  test("writes active backup to CloudKit when direct CloudKit backup is missing") {
    accountService.setActiveAccount(fullAccount)
    cloudStoreAccountRepository.currentAccountResult = Ok(iCloudStoreAccount)
    val iCloudCloudBackupService = realICloudCloudBackupService()
    service = newMigrationService(iCloudCloudBackupService)
    cloudBackupDao.set(fullAccount.accountId.serverId, CloudBackupV2WithFullAccountMock)
    val generatedBackup = CloudBackupV3WithFullAccountMock.copy(deviceNickname = "generated-current")
    fullAccountCloudBackupCreator.backupResult = Ok(generatedBackup)

    service.migrateIfNeeded().shouldBeOk()

    fullAccountCloudBackupCreator.createCalls.awaitItem()
    iCloudCloudBackupService.readActiveBackup(iCloudStoreAccount).shouldBeOk(generatedBackup)
  }

  test("ignores account-specific active CloudKit backup while shared backups are disabled") {
    accountService.setActiveAccount(fullAccount)
    cloudStoreAccountRepository.currentAccountResult = Ok(iCloudStoreAccount)
    val iCloudCloudBackupService = realICloudCloudBackupService(sharedBackupsEnabled = false)
    service = newMigrationService(iCloudCloudBackupService)
    cloudBackupDao.set(fullAccount.accountId.serverId, CloudBackupV2WithFullAccountMock)

    val accountSpecificKey = "cb-${fullAccount.accountId.serverId}"
    val ignoredAccountSpecificBackup = CloudBackupV3WithFullAccountMock.copy(
      createdAt = Instant.parse("2024-01-01T00:00:00Z"),
      deviceNickname = "account-specific-ignored"
    )
    val generatedLegacyBackup = CloudBackupV3WithFullAccountMock.copy(
      createdAt = Instant.parse("2024-02-01T00:00:00Z"),
      deviceNickname = "generated-legacy-current"
    )
    cloudKitKeyValueStore
      .set(iCloudStoreAccount, accountSpecificKey, ignoredAccountSpecificBackup.encodeForCloudKit())
      .shouldBeOk()
    fullAccountCloudBackupCreator.backupResult = Ok(generatedLegacyBackup)

    service.migrateIfNeeded().shouldBeOk()

    fullAccountCloudBackupCreator.createCalls.awaitItem()
    cloudKitKeyValueStore
      .get(iCloudStoreAccount, "cloud-backup")
      .shouldBeOk(generatedLegacyBackup.encodeForCloudKit())
    cloudKitKeyValueStore
      .get(iCloudStoreAccount, accountSpecificKey)
      .shouldBeOk(ignoredAccountSpecificBackup.encodeForCloudKit())
    iCloudCloudBackupService.readActiveBackup(iCloudStoreAccount).shouldBeOk(generatedLegacyBackup)
  }

  test("skips active backup reconciliation when iCloud account is already marked reconciled and current") {
    accountService.setActiveAccount(fullAccount)
    cloudStoreAccountRepository.currentAccountResult = Ok(iCloudStoreAccount)
    cloudKitBackupMigrationStatusDao.setReconciled(fullAccount.accountId, iCloudStoreAccount).shouldBeOk()
    val existingBackup = CloudBackupV3WithFullAccountMock.copy(deviceNickname = "cloudkit-current")
    cloudKitKeyValueStore
      .set(iCloudStoreAccount, "cb-${fullAccount.accountId.serverId}", existingBackup.encodeForCloudKit())
      .shouldBeOk()
    fullAccountCloudBackupCreator.backupResult = Ok(
      CloudBackupV3WithFullAccountMock.copy(deviceNickname = "should-not-generate")
    )

    service.migrateIfNeeded().shouldBeOk()

    fullAccountCloudBackupCreator.createCalls.expectNoEvents()
  }

  test("reconciles marked iCloud account when KVS active backup is fresher than CloudKit") {
    accountService.setActiveAccount(fullAccount)
    cloudStoreAccountRepository.currentAccountResult = Ok(iCloudStoreAccount)
    cloudKitBackupMigrationStatusDao.setReconciled(fullAccount.accountId, iCloudStoreAccount).shouldBeOk()
    val iCloudCloudBackupService = realICloudCloudBackupService()
    service = newMigrationService(iCloudCloudBackupService)

    val activeKey = "cb-${fullAccount.accountId.serverId}"
    val cloudKitBackup = CloudBackupV3WithFullAccountMock.copy(
      createdAt = Instant.parse("2024-01-01T00:00:00Z"),
      deviceNickname = "cloudkit-old"
    )
    val kvsBackup = CloudBackupV3WithFullAccountMock.copy(
      createdAt = Instant.parse("2024-03-01T00:00:00Z"),
      deviceNickname = "kvs-current"
    )
    cloudKitKeyValueStore
      .set(iCloudStoreAccount, activeKey, cloudKitBackup.encodeForCloudKit())
      .shouldBeOk()
    ubiquitousKeyValueStore
      .setString(iCloudStoreAccount, activeKey, kvsBackup.encodeForCloudKit().utf8())
      .shouldBeOk()

    service.migrateIfNeeded().shouldBeOk()

    fullAccountCloudBackupCreator.createCalls.expectNoEvents()
    cloudKitKeyValueStore.get(iCloudStoreAccount, activeKey).shouldBeOk(kvsBackup.encodeForCloudKit())
  }

  test("marks iCloud account reconciled after successful active backup reconciliation") {
    accountService.setActiveAccount(fullAccount)
    cloudStoreAccountRepository.currentAccountResult = Ok(iCloudStoreAccount)
    val iCloudCloudBackupService = realICloudCloudBackupService()
    service = newMigrationService(iCloudCloudBackupService)
    cloudBackupDao.set(fullAccount.accountId.serverId, CloudBackupV2WithFullAccountMock)
    val generatedBackup = CloudBackupV3WithFullAccountMock.copy(deviceNickname = "generated-once")
    fullAccountCloudBackupCreator.backupResult = Ok(generatedBackup)

    service.migrateIfNeeded().shouldBeOk()
    fullAccountCloudBackupCreator.createCalls.awaitItem()

    cloudKitBackupMigrationStatusDao.isReconciled(fullAccount.accountId, iCloudStoreAccount)
      .shouldBeOk(true)

    fullAccountCloudBackupCreator.reset()
    fullAccountCloudBackupCreator.backupResult = Ok(
      CloudBackupV3WithFullAccountMock.copy(deviceNickname = "should-not-regenerate")
    )
    service.migrateIfNeeded().shouldBeOk()

    fullAccountCloudBackupCreator.createCalls.expectNoEvents()
  }

  test("does not rewrite CloudKit when it is at least as fresh as KVS") {
    accountService.setActiveAccount(fullAccount)
    cloudStoreAccountRepository.currentAccountResult = Ok(iCloudStoreAccount)
    val iCloudCloudBackupService = realICloudCloudBackupService()
    service = newMigrationService(iCloudCloudBackupService)
    val activeKey = "cb-${fullAccount.accountId.serverId}"
    val cloudKitBackup = CloudBackupV3WithFullAccountMock.copy(
      createdAt = Instant.parse("2024-03-01T00:00:00Z"),
      deviceNickname = "cloudkit-current"
    )
    val kvsBackup = CloudBackupV3WithFullAccountMock.copy(
      createdAt = Instant.parse("2024-02-01T00:00:00Z"),
      deviceNickname = "kvs-old"
    )
    cloudKitKeyValueStore
      .set(iCloudStoreAccount, activeKey, cloudKitBackup.encodeForCloudKit())
      .shouldBeOk()
    ubiquitousKeyValueStore
      .setString(iCloudStoreAccount, activeKey, kvsBackup.encodeForCloudKit().utf8())
      .shouldBeOk()

    service.migrateIfNeeded().shouldBeOk()

    fullAccountCloudBackupCreator.createCalls.expectNoEvents()
    cloudKitKeyValueStore.get(iCloudStoreAccount, activeKey).shouldBeOk(cloudKitBackup.encodeForCloudKit())
    logWriter.logs.any {
      it.severity == Severity.Info &&
        it.message == "CloudKit active backup migration started for account [${fullAccount.accountId.serverId}]"
    }.shouldBe(false)
  }

  test("rewrites same-account CloudKit backup when KVS is fresher") {
    accountService.setActiveAccount(fullAccount)
    cloudStoreAccountRepository.currentAccountResult = Ok(iCloudStoreAccount)
    val iCloudCloudBackupService = realICloudCloudBackupService()
    service = newMigrationService(iCloudCloudBackupService)
    val activeKey = "cb-${fullAccount.accountId.serverId}"
    val staleBackup = CloudBackupV3WithFullAccountMock.copy(
      createdAt = Instant.parse("2024-01-01T00:00:00Z"),
      deviceNickname = "stale-cloudkit"
    )
    val kvsBackup = CloudBackupV3WithFullAccountMock.copy(
      createdAt = Instant.parse("2024-03-01T00:00:00Z"),
      deviceNickname = "kvs-current"
    )
    cloudKitKeyValueStore
      .set(iCloudStoreAccount, activeKey, staleBackup.encodeForCloudKit())
      .shouldBeOk()
    ubiquitousKeyValueStore
      .setString(iCloudStoreAccount, activeKey, kvsBackup.encodeForCloudKit().utf8())
      .shouldBeOk()

    service.migrateIfNeeded().shouldBeOk()

    fullAccountCloudBackupCreator.createCalls.expectNoEvents()
    cloudKitKeyValueStore.get(iCloudStoreAccount, activeKey).shouldBeOk(kvsBackup.encodeForCloudKit())
  }

  test("rewrites same-account CloudKit backup from legacy KVS when account-specific KVS is stale") {
    accountService.setActiveAccount(fullAccount)
    cloudStoreAccountRepository.currentAccountResult = Ok(iCloudStoreAccount)
    val iCloudCloudBackupService = realICloudCloudBackupService()
    service = newMigrationService(iCloudCloudBackupService)
    val activeKey = "cb-${fullAccount.accountId.serverId}"
    val staleBackup = CloudBackupV3WithFullAccountMock.copy(
      createdAt = Instant.parse("2024-01-01T00:00:00Z"),
      deviceNickname = "stale-cloudkit"
    )
    val fresherLegacyBackup = CloudBackupV3WithFullAccountMock.copy(
      createdAt = Instant.parse("2024-03-01T00:00:00Z"),
      deviceNickname = "legacy-kvs-current"
    )
    cloudKitKeyValueStore
      .set(iCloudStoreAccount, activeKey, staleBackup.encodeForCloudKit())
      .shouldBeOk()
    ubiquitousKeyValueStore
      .setString(iCloudStoreAccount, activeKey, staleBackup.encodeForCloudKit().utf8())
      .shouldBeOk()
    ubiquitousKeyValueStore
      .setString(iCloudStoreAccount, "cloud-backup", fresherLegacyBackup.encodeForCloudKit().utf8())
      .shouldBeOk()

    service.migrateIfNeeded().shouldBeOk()

    fullAccountCloudBackupCreator.createCalls.expectNoEvents()
    cloudKitKeyValueStore.get(iCloudStoreAccount, activeKey).shouldBeOk(fresherLegacyBackup.encodeForCloudKit())
  }

  test("preserves same-account CloudKit backup when KVS is not fresher") {
    accountService.setActiveAccount(fullAccount)
    cloudStoreAccountRepository.currentAccountResult = Ok(iCloudStoreAccount)
    val iCloudCloudBackupService = realICloudCloudBackupService()
    service = newMigrationService(iCloudCloudBackupService)
    val activeKey = "cb-${fullAccount.accountId.serverId}"
    val cloudKitBackup = CloudBackupV3WithFullAccountMock.copy(
      createdAt = Instant.parse("2024-03-01T00:00:00Z"),
      deviceNickname = "cloudkit-current"
    )
    val kvsBackup = CloudBackupV3WithFullAccountMock.copy(
      createdAt = Instant.parse("2024-02-01T00:00:00Z"),
      deviceNickname = "kvs-old"
    )
    cloudKitKeyValueStore
      .set(iCloudStoreAccount, activeKey, cloudKitBackup.encodeForCloudKit())
      .shouldBeOk()
    ubiquitousKeyValueStore
      .setString(iCloudStoreAccount, activeKey, kvsBackup.encodeForCloudKit().utf8())
      .shouldBeOk()

    service.migrateIfNeeded().shouldBeOk()

    fullAccountCloudBackupCreator.createCalls.expectNoEvents()
    cloudKitKeyValueStore.get(iCloudStoreAccount, activeKey).shouldBeOk(cloudKitBackup.encodeForCloudKit())
  }

  test("continues active migration when CloudKit has active key for different account") {
    accountService.setActiveAccount(fullAccount)
    cloudStoreAccountRepository.currentAccountResult = Ok(iCloudStoreAccount)
    val iCloudCloudBackupService = realICloudCloudBackupService()
    service = newMigrationService(iCloudCloudBackupService)
    val differentAccountActiveKey = "cb-${liteAccount.accountId.serverId}"
    val otherAccountBackup = CloudBackupV3WithFullAccountMock.copy(
      accountId = liteAccount.accountId.serverId,
      deviceNickname = "other-account"
    )
    cloudKitKeyValueStore
      .set(iCloudStoreAccount, differentAccountActiveKey, otherAccountBackup.encodeForCloudKit())
      .shouldBeOk()
    cloudBackupDao.set(fullAccount.accountId.serverId, CloudBackupV2WithFullAccountMock)
    val migratedBackup = CloudBackupV3WithFullAccountMock.copy(deviceNickname = "migrated-active")
    fullAccountCloudBackupCreator.backupResult = Ok(migratedBackup)

    service.migrateIfNeeded().shouldBeOk()

    fullAccountCloudBackupCreator.createCalls.awaitItem()
    iCloudCloudBackupService.readActiveBackup(iCloudStoreAccount).shouldBeOk(migratedBackup)
    cloudKitKeyValueStore
      .get(iCloudStoreAccount, differentAccountActiveKey)
      .shouldBeOk(otherAccountBackup.encodeForCloudKit())
  }

  test("continues active migration when CloudKit has legacy active key for different account") {
    accountService.setActiveAccount(fullAccount)
    cloudStoreAccountRepository.currentAccountResult = Ok(iCloudStoreAccount)
    val iCloudCloudBackupService = realICloudCloudBackupService()
    service = newMigrationService(iCloudCloudBackupService)
    val encodedLegacyBackup = jsonSerializer
      .encodeToStringResult(CloudBackupV2WithFullAccountMock.copy(accountId = liteAccount.accountId.serverId))
      .shouldBeOk()
    cloudKitKeyValueStore
      .set(iCloudStoreAccount, "cloud-backup", encodedLegacyBackup.encodeUtf8())
      .shouldBeOk()
    cloudBackupDao.set(fullAccount.accountId.serverId, CloudBackupV2WithFullAccountMock)
    val migratedBackup = CloudBackupV3WithFullAccountMock.copy(deviceNickname = "migrated-active")
    fullAccountCloudBackupCreator.backupResult = Ok(migratedBackup)

    service.migrateIfNeeded().shouldBeOk()

    fullAccountCloudBackupCreator.createCalls.awaitItem()
    iCloudCloudBackupService.readActiveBackup(iCloudStoreAccount).shouldBeOk(migratedBackup)
  }

  test("continues active migration when CloudKit legacy active key is malformed") {
    accountService.setActiveAccount(fullAccount)
    cloudStoreAccountRepository.currentAccountResult = Ok(iCloudStoreAccount)
    val iCloudCloudBackupService = realICloudCloudBackupService()
    service = newMigrationService(iCloudCloudBackupService)
    cloudKitKeyValueStore.set(iCloudStoreAccount, "cloud-backup", "malformed".encodeUtf8()).shouldBeOk()
    cloudBackupDao.set(fullAccount.accountId.serverId, CloudBackupV2WithFullAccountMock)
    val migratedBackup = CloudBackupV3WithFullAccountMock.copy(deviceNickname = "migrated-active")
    fullAccountCloudBackupCreator.backupResult = Ok(migratedBackup)

    service.migrateIfNeeded().shouldBeOk()

    fullAccountCloudBackupCreator.createCalls.awaitItem()
    iCloudCloudBackupService.readActiveBackup(iCloudStoreAccount).shouldBeOk(migratedBackup)
    logWriter.logs.any {
      it.severity == Severity.Warn &&
        it.message == "CloudKit active backup read failed at key [cloud-backup]; continuing reconciliation."
    }.shouldBe(true)
  }

  test("migrates archived keys before skipping active migration") {
    accountService.setActiveAccount(fullAccount)
    cloudStoreAccountRepository.currentAccountResult = Ok(iCloudStoreAccount)
    val activeKey = "cb-${fullAccount.accountId.serverId}"
    val archivedKey = "cb-${fullAccount.accountId.serverId}-2024-01-01T12:00:00Z"
    cloudKitKeyValueStore.set(iCloudStoreAccount, activeKey, "existing-value".encodeUtf8()).shouldBeOk()
    ubiquitousKeyValueStore.setString(iCloudStoreAccount, archivedKey, "archived-value").shouldBeOk()

    service.migrateIfNeeded().shouldBeOk()

    fullAccountCloudBackupCreator.createCalls.expectNoEvents()
    cloudKitKeyValueStore.get(iCloudStoreAccount, archivedKey).shouldBeOk("archived-value".encodeUtf8())
  }

  test("does not skip migration based on non-CloudKit active backup reads") {
    accountService.setActiveAccount(fullAccount)
    cloudStoreAccountRepository.currentAccountResult = Ok(iCloudStoreAccount)
    val iCloudCloudBackupService = realICloudCloudBackupService()
    service = newMigrationService(iCloudCloudBackupService)
    cloudBackupDao.set(fullAccount.accountId.serverId, CloudBackupV2WithFullAccountMock)
    val staleBackup = CloudBackupV3WithFullAccountMock.copy(deviceNickname = "stale-non-cloudkit")
    cloudBackupService.writeBackup(
      accountId = fullAccount.accountId,
      cloudStoreAccount = iCloudStoreAccount,
      backup = staleBackup,
      requireAuthRefresh = false
    )
    val migratedBackup = CloudBackupV3WithFullAccountMock.copy(deviceNickname = "migrated-active")
    fullAccountCloudBackupCreator.backupResult = Ok(migratedBackup)

    service.migrateIfNeeded().shouldBeOk()
    fullAccountCloudBackupCreator.createCalls.awaitItem()
    iCloudCloudBackupService.readActiveBackup(iCloudStoreAccount).shouldBeOk(migratedBackup)
  }

  test("continues active backup migration when archived KVS migration fails") {
    accountService.setActiveAccount(fullAccount)
    cloudStoreAccountRepository.currentAccountResult = Ok(iCloudStoreAccount)
    val iCloudCloudBackupService = realICloudCloudBackupService()
    service = newMigrationService(iCloudCloudBackupService)
    ubiquitousKeyValueStore.returnError = true
    cloudBackupDao.set(fullAccount.accountId.serverId, CloudBackupV2WithFullAccountMock)
    val migratedBackup = CloudBackupV3WithFullAccountMock.copy(deviceNickname = "migrated-active")
    fullAccountCloudBackupCreator.backupResult = Ok(migratedBackup)

    service.migrateIfNeeded().shouldBeOk()
    fullAccountCloudBackupCreator.createCalls.awaitItem()

    iCloudCloudBackupService.readActiveBackup(iCloudStoreAccount).shouldBeOk(migratedBackup)
    logWriter.logs.any {
      it.severity == Severity.Warn &&
        it.message == "CloudKit archived backup migration failed; continuing active backup migration"
    }.shouldBe(true)
  }

  test("returns error when active CloudKit write fails after archived CloudKit migration fails") {
    accountService.setActiveAccount(fullAccount)
    cloudStoreAccountRepository.currentAccountResult = Ok(iCloudStoreAccount)
    val iCloudCloudBackupService = realICloudCloudBackupService()
    service = newMigrationService(iCloudCloudBackupService)
    val archivedKey = "cb-${fullAccount.accountId.serverId}-2024-01-01T12:00:00Z"
    ubiquitousKeyValueStore.setString(iCloudStoreAccount, archivedKey, "archived-value").shouldBeOk()
    cloudKitKeyValueStore.returnError = true
    cloudBackupDao.set(fullAccount.accountId.serverId, CloudBackupV2WithFullAccountMock)
    val migratedBackup = CloudBackupV3WithFullAccountMock.copy(deviceNickname = "migrated-active")
    fullAccountCloudBackupCreator.backupResult = Ok(migratedBackup)

    service.migrateIfNeeded().shouldBeErrOfType<Throwable>()
    fullAccountCloudBackupCreator.createCalls.awaitItem()

    logWriter.logs.any {
      it.severity == Severity.Warn &&
        it.message == "CloudKit archived backup migration failed; continuing active backup migration"
    }.shouldBe(true)
  }

  test("returns error when CloudKit write cannot be verified by direct read-back") {
    accountService.setActiveAccount(fullAccount)
    cloudStoreAccountRepository.currentAccountResult = Ok(iCloudStoreAccount)
    cloudBackupDao.set(fullAccount.accountId.serverId, CloudBackupV2WithFullAccountMock)
    val migratedBackup = CloudBackupV3WithFullAccountMock.copy(deviceNickname = "unverified-active")
    fullAccountCloudBackupCreator.backupResult = Ok(migratedBackup)

    service.migrateIfNeeded().shouldBeErrOfType<Throwable>()

    fullAccountCloudBackupCreator.createCalls.awaitItem()
    logWriter.logs.any {
      it.severity == Severity.Warn &&
        it.message == "CloudKit active backup read-back verification failed for key [cloud-backup]"
    }.shouldBe(true)
  }
})
