package build.wallet.cloud.backup.migration

import build.wallet.account.AccountServiceFake
import build.wallet.bitkey.keybox.FullAccountMock
import build.wallet.bitkey.keybox.LiteAccountMock
import build.wallet.bitkey.keybox.SoftwareAccountMock
import build.wallet.cloud.backup.CloudBackupError
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
import build.wallet.feature.flags.SharedCloudBackupsFeatureFlag
import build.wallet.logging.LogLevel
import build.wallet.logging.LogWriterMock
import build.wallet.logging.Logger
import build.wallet.testing.shouldBeOk
import build.wallet.time.ClockFake
import co.touchlab.kermit.Severity
import com.github.michaelbull.result.Ok
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import okio.ByteString.Companion.encodeUtf8

class CloudKitBackupMigrationServiceTests : FunSpec({
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
  val logWriter = LogWriterMock()
  val fullAccountCloudBackupCreator = FullAccountCloudBackupCreatorMock(turbines::create)
  val liteAccountCloudBackupCreator = LiteAccountCloudBackupCreatorMock()

  val cloudAccount = CloudAccountMock("test-cloud-instance")
  val iCloudStoreAccount = iCloudAccount(ubiquityIdentityToken = "test-ubiquity-identity-token")
  val fullAccount = FullAccountMock
  val liteAccount = LiteAccountMock

  lateinit var service: CloudKitBackupMigrationServiceImpl

  beforeTest {
    accountService.reset()
    cloudStoreAccountRepository.reset()
    cloudBackupService.reset()
    cloudBackupDao.reset()
    cloudKitKeyValueStore.reset()
    ubiquitousKeyValueStore.reset()
    logWriter.clear()
    fullAccountCloudBackupCreator.reset()
    liteAccountCloudBackupCreator.reset()
    cloudStoreAccountRepository.currentAccountResult = Ok(cloudAccount)
    Logger.configure(
      tag = "CloudKitBackupMigrationServiceTests",
      minimumLogLevel = LogLevel.Verbose,
      logWriters = listOf(logWriter)
    )

    service = CloudKitBackupMigrationServiceImpl(
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

  test("skips active migration when CloudKit already has active account key") {
    accountService.setActiveAccount(fullAccount)
    cloudStoreAccountRepository.currentAccountResult = Ok(iCloudStoreAccount)
    val activeKey = "cb-${fullAccount.accountId.serverId}"
    cloudKitKeyValueStore.set(iCloudStoreAccount, activeKey, "existing-value".encodeUtf8()).shouldBeOk()

    service.migrateIfNeeded().shouldBeOk()

    fullAccountCloudBackupCreator.createCalls.expectNoEvents()
  }

  test("skips active migration when CloudKit has legacy active backup key") {
    accountService.setActiveAccount(fullAccount)
    cloudStoreAccountRepository.currentAccountResult = Ok(iCloudStoreAccount)
    val encodedLegacyBackup = jsonSerializer
      .encodeToStringResult(CloudBackupV2WithFullAccountMock.copy(accountId = fullAccount.accountId.serverId))
      .shouldBeOk()
    cloudKitKeyValueStore
      .set(iCloudStoreAccount, "cloud-backup", encodedLegacyBackup.encodeUtf8())
      .shouldBeOk()

    service.migrateIfNeeded().shouldBeOk()

    fullAccountCloudBackupCreator.createCalls.expectNoEvents()
  }

  test("continues active migration when CloudKit has active key for different account") {
    accountService.setActiveAccount(fullAccount)
    cloudStoreAccountRepository.currentAccountResult = Ok(iCloudStoreAccount)
    val differentAccountActiveKey = "cb-${liteAccount.accountId.serverId}"
    cloudKitKeyValueStore
      .set(iCloudStoreAccount, differentAccountActiveKey, "existing-value".encodeUtf8())
      .shouldBeOk()
    cloudBackupDao.set(fullAccount.accountId.serverId, CloudBackupV2WithFullAccountMock)
    val migratedBackup = CloudBackupV3WithFullAccountMock.copy(deviceNickname = "migrated-active")
    fullAccountCloudBackupCreator.backupResult = Ok(migratedBackup)

    service.migrateIfNeeded().shouldBeOk()

    fullAccountCloudBackupCreator.createCalls.awaitItem()
    cloudBackupService.readActiveBackup(iCloudStoreAccount).shouldBeOk(migratedBackup)
  }

  test("continues active migration when CloudKit has legacy active key for different account") {
    accountService.setActiveAccount(fullAccount)
    cloudStoreAccountRepository.currentAccountResult = Ok(iCloudStoreAccount)
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
    cloudBackupService.readActiveBackup(iCloudStoreAccount).shouldBeOk(migratedBackup)
  }

  test("continues active migration when CloudKit legacy active key is malformed") {
    accountService.setActiveAccount(fullAccount)
    cloudStoreAccountRepository.currentAccountResult = Ok(iCloudStoreAccount)
    cloudKitKeyValueStore.set(iCloudStoreAccount, "cloud-backup", "malformed".encodeUtf8()).shouldBeOk()
    cloudBackupDao.set(fullAccount.accountId.serverId, CloudBackupV2WithFullAccountMock)
    val migratedBackup = CloudBackupV3WithFullAccountMock.copy(deviceNickname = "migrated-active")
    fullAccountCloudBackupCreator.backupResult = Ok(migratedBackup)

    service.migrateIfNeeded().shouldBeOk()

    fullAccountCloudBackupCreator.createCalls.awaitItem()
    cloudBackupService.readActiveBackup(iCloudStoreAccount).shouldBeOk(migratedBackup)
    logWriter.logs.any {
      it.severity == Severity.Warn &&
        it.message == "CloudKit active-backup presence check failed; continuing migration write"
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
    cloudBackupService.readActiveBackup(iCloudStoreAccount).shouldBeOk(migratedBackup)
  }

  test("continues active backup migration when archived KVS migration fails") {
    accountService.setActiveAccount(fullAccount)
    cloudStoreAccountRepository.currentAccountResult = Ok(iCloudStoreAccount)
    ubiquitousKeyValueStore.returnError = true
    cloudBackupDao.set(fullAccount.accountId.serverId, CloudBackupV2WithFullAccountMock)
    val migratedBackup = CloudBackupV3WithFullAccountMock.copy(deviceNickname = "migrated-active")
    fullAccountCloudBackupCreator.backupResult = Ok(migratedBackup)

    service.migrateIfNeeded().shouldBeOk()
    fullAccountCloudBackupCreator.createCalls.awaitItem()

    cloudBackupService.readActiveBackup(iCloudStoreAccount).shouldBeOk(migratedBackup)
    logWriter.logs.any {
      it.severity == Severity.Warn &&
        it.message == "CloudKit archived backup migration failed; continuing active backup migration"
    }.shouldBe(true)
  }

  test("continues active backup migration when archived CloudKit migration fails") {
    accountService.setActiveAccount(fullAccount)
    cloudStoreAccountRepository.currentAccountResult = Ok(iCloudStoreAccount)
    val archivedKey = "cb-${fullAccount.accountId.serverId}-2024-01-01T12:00:00Z"
    ubiquitousKeyValueStore.setString(iCloudStoreAccount, archivedKey, "archived-value").shouldBeOk()
    cloudKitKeyValueStore.returnError = true
    cloudBackupDao.set(fullAccount.accountId.serverId, CloudBackupV2WithFullAccountMock)
    val migratedBackup = CloudBackupV3WithFullAccountMock.copy(deviceNickname = "migrated-active")
    fullAccountCloudBackupCreator.backupResult = Ok(migratedBackup)

    service.migrateIfNeeded().shouldBeOk()
    fullAccountCloudBackupCreator.createCalls.awaitItem()

    cloudBackupService.readActiveBackup(iCloudStoreAccount).shouldBeOk(migratedBackup)
    logWriter.logs.any {
      it.severity == Severity.Warn &&
        it.message == "CloudKit archived backup migration failed; continuing active backup migration"
    }.shouldBe(true)
  }
})
