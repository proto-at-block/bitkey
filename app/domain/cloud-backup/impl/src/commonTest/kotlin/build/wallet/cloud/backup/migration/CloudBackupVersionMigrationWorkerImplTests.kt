package build.wallet.cloud.backup.migration

import build.wallet.account.AccountServiceFake
import build.wallet.bitkey.keybox.FullAccountMock
import build.wallet.bitkey.keybox.LiteAccountMock
import build.wallet.cloud.backup.CloudBackupError
import build.wallet.cloud.backup.CloudBackupRepositoryFake
import build.wallet.cloud.backup.CloudBackupV2WithFullAccountMock
import build.wallet.cloud.backup.CloudBackupV2WithLiteAccountMock
import build.wallet.cloud.backup.CloudBackupV3
import build.wallet.cloud.backup.CloudBackupV3WithFullAccountMock
import build.wallet.cloud.backup.CloudBackupV3WithLiteAccountMock
import build.wallet.cloud.backup.FullAccountCloudBackupCreator.FullAccountCloudBackupCreatorError
import build.wallet.cloud.backup.FullAccountCloudBackupCreatorMock
import build.wallet.cloud.backup.LiteAccountCloudBackupCreator.LiteAccountCloudBackupCreatorError
import build.wallet.cloud.backup.LiteAccountCloudBackupCreatorMock
import build.wallet.cloud.backup.awaitBackup
import build.wallet.cloud.backup.local.CloudBackupDaoFake
import build.wallet.cloud.store.CloudAccountMock
import build.wallet.cloud.store.CloudStoreAccountRepositoryMock
import build.wallet.coroutines.turbine.turbines
import build.wallet.platform.app.AppSessionManagerFake
import com.github.michaelbull.result.Err
import com.github.michaelbull.result.Ok
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeTypeOf

class CloudBackupVersionMigrationWorkerImplTests : FunSpec({

  val accountService = AccountServiceFake()
  val cloudStoreAccountRepository = CloudStoreAccountRepositoryMock()
  val cloudBackupRepository = CloudBackupRepositoryFake()
  val fullAccountCloudBackupCreator = FullAccountCloudBackupCreatorMock(turbines::create)
  val liteAccountCloudBackupCreator = LiteAccountCloudBackupCreatorMock()
  val appSessionManager = AppSessionManagerFake()
  val cloudBackupDao = CloudBackupDaoFake()

  val cloudAccount = CloudAccountMock("test-cloud-instance")
  val fullAccount = FullAccountMock
  val liteAccount = LiteAccountMock

  val worker = CloudBackupVersionMigrationWorkerImpl(
    accountService = accountService,
    cloudStoreAccountRepository = cloudStoreAccountRepository,
    cloudBackupRepository = cloudBackupRepository,
    fullAccountCloudBackupCreator = fullAccountCloudBackupCreator,
    liteAccountCloudBackupCreator = liteAccountCloudBackupCreator,
    cloudBackupDao = cloudBackupDao,
    appSessionManager = appSessionManager
  )

  beforeTest {
    accountService.reset()
    cloudStoreAccountRepository.reset()
    cloudBackupRepository.reset()
    fullAccountCloudBackupCreator.reset()
    liteAccountCloudBackupCreator.reset()
    cloudBackupDao.reset()
    cloudStoreAccountRepository.currentAccountResult = Ok(cloudAccount)
  }

  test("migrates V2 full account backup to V3") {
    accountService.setActiveAccount(fullAccount)
    cloudBackupDao.set(fullAccount.accountId.serverId, CloudBackupV2WithFullAccountMock)
    cloudBackupRepository.writeBackup(
      accountId = fullAccount.accountId,
      cloudStoreAccount = cloudAccount,
      backup = CloudBackupV2WithFullAccountMock,
      requireAuthRefresh = false
    )
    fullAccountCloudBackupCreator.backupResult = Ok(CloudBackupV3WithFullAccountMock)

    worker.executeWork()

    // Verify backup creator was called
    fullAccountCloudBackupCreator.createCalls.awaitItem()
      .shouldNotBeNull()

    // Verify V3 backup was written to cloud
    val writtenBackup = cloudBackupRepository.awaitBackup(cloudAccount)
    writtenBackup.shouldBeTypeOf<CloudBackupV3>()
  }

  test("migrates V2 lite account backup to V3") {
    accountService.setActiveAccount(liteAccount)
    cloudBackupDao.set(liteAccount.accountId.serverId, CloudBackupV2WithLiteAccountMock)
    cloudBackupRepository.writeBackup(
      accountId = liteAccount.accountId,
      cloudStoreAccount = cloudAccount,
      backup = CloudBackupV2WithLiteAccountMock,
      requireAuthRefresh = false
    )
    liteAccountCloudBackupCreator.createResultCreator = { Ok(CloudBackupV3WithLiteAccountMock) }

    worker.executeWork()

    // Verify V3 backup was written to cloud
    val writtenBackup = cloudBackupRepository.awaitBackup(cloudAccount)
    writtenBackup.shouldBeTypeOf<CloudBackupV3>()
  }

  test("skips migration when backup is already V3") {
    accountService.setActiveAccount(fullAccount)
    cloudBackupDao.set(fullAccount.accountId.serverId, CloudBackupV3WithFullAccountMock)
    cloudBackupRepository.writeBackup(
      accountId = fullAccount.accountId,
      cloudStoreAccount = cloudAccount,
      backup = CloudBackupV3WithFullAccountMock,
      requireAuthRefresh = false
    )

    worker.executeWork()

    // Verify no backup was created
    fullAccountCloudBackupCreator.createCalls.expectNoEvents()

    // Verify backup remains V3 (no re-write)
    val backup = cloudBackupRepository.readActiveBackup(cloudAccount).value
    backup.shouldBe(CloudBackupV3WithFullAccountMock)
  }

  test("skips migration when no local backup exists") {
    accountService.setActiveAccount(fullAccount)
    // No backup set in repository

    worker.executeWork()

    // Verify no migration attempted
    fullAccountCloudBackupCreator.createCalls.expectNoEvents()

    // Verify no backup exists
    val backup = cloudBackupRepository.readActiveBackup(cloudAccount).value
    backup.shouldBe(null)
  }

  test("skips migration when no active account") {
    // No account set

    worker.executeWork()

    // Verify no migration attempted
    fullAccountCloudBackupCreator.createCalls.expectNoEvents()

    // Verify no backup exists
    val backup = cloudBackupRepository.readActiveBackup(cloudAccount).value
    backup.shouldBe(null)
  }

  test("handles missing cloud store account gracefully") {
    accountService.setActiveAccount(fullAccount)
    cloudBackupDao.set(fullAccount.accountId.serverId, CloudBackupV2WithFullAccountMock)
    cloudBackupRepository.writeBackup(
      accountId = fullAccount.accountId,
      cloudStoreAccount = cloudAccount,
      backup = CloudBackupV2WithFullAccountMock,
      requireAuthRefresh = false
    )
    cloudStoreAccountRepository.currentAccountResult = Ok(null)

    worker.executeWork()

    // Verify no migration attempted
    fullAccountCloudBackupCreator.createCalls.expectNoEvents()

    // Verify backup remains V2 (no migration happened)
    val backup = cloudBackupRepository.readActiveBackup(cloudAccount).value
    backup.shouldBe(CloudBackupV2WithFullAccountMock)
  }

  test("handles backup creation error gracefully for full account") {
    accountService.setActiveAccount(fullAccount)
    cloudBackupDao.set(fullAccount.accountId.serverId, CloudBackupV2WithFullAccountMock)
    cloudBackupRepository.writeBackup(
      accountId = fullAccount.accountId,
      cloudStoreAccount = cloudAccount,
      backup = CloudBackupV2WithFullAccountMock,
      requireAuthRefresh = false
    )
    fullAccountCloudBackupCreator.backupResult =
      Err(FullAccountCloudBackupCreatorError.FullAccountFieldsCreationError(cause = Exception("Creation failed")))

    worker.executeWork()

    // Verify creation was attempted but failed gracefully
    fullAccountCloudBackupCreator.createCalls.awaitItem()

    // Verify backup remains V2 (not updated due to creation error)
    val backup = cloudBackupRepository.readActiveBackup(cloudAccount).value
    backup.shouldBe(CloudBackupV2WithFullAccountMock)
  }

  test("handles backup creation error gracefully for lite account") {
    accountService.setActiveAccount(liteAccount)
    cloudBackupDao.set(liteAccount.accountId.serverId, CloudBackupV2WithLiteAccountMock)
    cloudBackupRepository.writeBackup(
      accountId = liteAccount.accountId,
      cloudStoreAccount = cloudAccount,
      backup = CloudBackupV2WithLiteAccountMock,
      requireAuthRefresh = false
    )
    liteAccountCloudBackupCreator.createResultCreator = {
      Err(LiteAccountCloudBackupCreatorError.SocRecKeysRetrievalError(cause = Exception("Creation failed")))
    }

    worker.executeWork()

    // Verify backup remains V2 (not updated due to creation error)
    val backup = cloudBackupRepository.readActiveBackup(cloudAccount).value
    backup.shouldBe(CloudBackupV2WithLiteAccountMock)
  }

  test("handles cloud backup upload error gracefully") {
    accountService.setActiveAccount(fullAccount)
    cloudBackupDao.set(fullAccount.accountId.serverId, CloudBackupV2WithFullAccountMock)
    cloudBackupRepository.writeBackup(
      accountId = fullAccount.accountId,
      cloudStoreAccount = cloudAccount,
      backup = CloudBackupV2WithFullAccountMock,
      requireAuthRefresh = false
    )
    fullAccountCloudBackupCreator.backupResult = Ok(CloudBackupV3WithFullAccountMock)
    cloudBackupRepository.returnWriteError =
      CloudBackupError.UnrectifiableCloudBackupError(Throwable("Upload failed"))

    worker.executeWork()

    // Verify backup was created but upload failed
    fullAccountCloudBackupCreator.createCalls.awaitItem()

    // Verify backup remains V2 (not updated due to upload error)
    // Note: We need to clear the write error to read the backup
    cloudBackupRepository.returnWriteError = null
    val backup = cloudBackupRepository.readActiveBackup(cloudAccount).value
    backup.shouldBe(CloudBackupV2WithFullAccountMock)
  }

  test("skips migration for V2 full account backup with no full account fields") {
    accountService.setActiveAccount(fullAccount)
    val v2WithoutFields = CloudBackupV2WithFullAccountMock.copy(fullAccountFields = null)
    cloudBackupDao.set(fullAccount.accountId.serverId, v2WithoutFields)
    cloudBackupRepository.writeBackup(
      accountId = fullAccount.accountId,
      cloudStoreAccount = cloudAccount,
      backup = v2WithoutFields,
      requireAuthRefresh = false
    )

    worker.executeWork()

    // Verify no migration attempted (backup is invalid)
    fullAccountCloudBackupCreator.createCalls.expectNoEvents()

    // Verify backup remains unchanged
    val backup = cloudBackupRepository.readActiveBackup(cloudAccount).value
    backup.shouldBe(v2WithoutFields)
  }

  test("extracts sealedCsek from V2 full account backup for migration") {
    accountService.setActiveAccount(fullAccount)
    val v2Backup = CloudBackupV2WithFullAccountMock
    cloudBackupDao.set(fullAccount.accountId.serverId, v2Backup)
    cloudBackupRepository.writeBackup(
      accountId = fullAccount.accountId,
      cloudStoreAccount = cloudAccount,
      backup = v2Backup,
      requireAuthRefresh = false
    )
    fullAccountCloudBackupCreator.backupResult = Ok(CloudBackupV3WithFullAccountMock)

    worker.executeWork()

    // Verify creator was called with the correct sealedCsek from V2 backup
    val (sealedCsek, _) = fullAccountCloudBackupCreator.createCalls.awaitItem() as Pair<*, *>

    val expectedCsek = v2Backup.fullAccountFields?.sealedHwEncryptionKey
    expectedCsek.shouldNotBeNull()
    sealedCsek.shouldBe(expectedCsek)
  }
})
