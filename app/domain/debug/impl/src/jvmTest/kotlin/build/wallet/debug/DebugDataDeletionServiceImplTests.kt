package build.wallet.debug

import build.wallet.account.AccountServiceFake
import build.wallet.bitcoin.AppPrivateKeyDaoFake
import build.wallet.bitkey.f8e.AccountId
import build.wallet.bitkey.keybox.FullAccountMock
import build.wallet.bitkey.spending.AppSpendingKeypair
import build.wallet.cloud.backup.csek.CsekDaoFake
import build.wallet.debug.DebugDataDeletionTarget.ActiveAccountCloudBackup
import build.wallet.debug.DebugDataDeletionTarget.ActiveAppSpendingKey
import build.wallet.debug.DebugDataDeletionTarget.AllLocalAppData
import build.wallet.debug.DebugDataDeletionTarget.AllCloudBackupStores
import build.wallet.debug.DebugDataDeletionTarget.CorruptCloudBackup
import build.wallet.debug.DebugDataDeletionTarget.CloudBackupsInStore
import build.wallet.debug.cloud.CloudBackupCorrupter
import build.wallet.debug.cloud.CloudBackupDeleter
import build.wallet.debug.cloud.CloudBackupKeysetDeleter
import build.wallet.debug.cloud.CloudBackupStoreType
import build.wallet.debug.cloud.CloudStore
import build.wallet.debug.cloud.CorruptionError
import build.wallet.debug.cloud.KeysetDeletionError
import build.wallet.keybox.keys.OnboardingAppKeyKeystoreFake
import build.wallet.onboarding.OnboardingKeyboxHardwareKeysDaoFake
import build.wallet.onboarding.OnboardingKeyboxSealedCsekDaoMock
import build.wallet.onboarding.OnboardingKeyboxSealedSsekDaoFake
import build.wallet.onboarding.OnboardingKeyboxStepStateDaoFake
import build.wallet.platform.config.AppVariant.Customer
import build.wallet.platform.config.AppVariant.Development
import build.wallet.recovery.DescriptorBackupVerificationDaoFake
import build.wallet.relationships.RelationshipsKeysDaoFake
import com.github.michaelbull.result.Ok
import com.github.michaelbull.result.Result
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.maps.shouldNotContainKey
import io.kotest.matchers.shouldBe

class DebugDataDeletionServiceImplTests : FunSpec({
  val accountService = AccountServiceFake()
  val appDataDeleter = AppDataDeleterFake()
  val appPrivateKeyDao = AppPrivateKeyDaoFake()
  val cloudBackupCorrupter = CloudBackupCorrupterFake()
  val cloudBackupDeleter = CloudBackupDeleterFake()
  val cloudBackupKeysetDeleter = CloudBackupKeysetDeleterFake()
  val descriptorBackupVerificationDao = DescriptorBackupVerificationDaoFake()

  fun service(appVariant: build.wallet.platform.config.AppVariant = Development) =
    DebugDataDeletionServiceImpl(
      appVariant = appVariant,
      accountService = accountService,
      appDataDeleter = appDataDeleter,
      appPrivateKeyDao = appPrivateKeyDao,
      cloudBackupCorrupter = cloudBackupCorrupter,
      cloudBackupDeleter = cloudBackupDeleter,
      cloudBackupKeysetDeleter = cloudBackupKeysetDeleter,
      csekDao = CsekDaoFake(),
      descriptorBackupVerificationDao = descriptorBackupVerificationDao,
      onboardingAppKeyKeystore = OnboardingAppKeyKeystoreFake(),
      onboardingKeyboxHardwareKeysDao = OnboardingKeyboxHardwareKeysDaoFake(),
      onboardingKeyboxSealedCsekDao = OnboardingKeyboxSealedCsekDaoMock(),
      onboardingKeyboxSealedSsekDao = OnboardingKeyboxSealedSsekDaoFake(),
      onboardingKeyboxStepStateDao = OnboardingKeyboxStepStateDaoFake(),
      relationshipsKeysDao = RelationshipsKeysDaoFake()
    )

  beforeTest {
    accountService.reset()
    appDataDeleter.reset()
    appPrivateKeyDao.reset()
    cloudBackupCorrupter.reset()
    cloudBackupDeleter.reset()
    cloudBackupKeysetDeleter.reset()
    descriptorBackupVerificationDao.reset()
  }

  test("deletes cloud backups in a specific store") {
    val report = service().delete(listOf(CloudBackupsInStore(CloudStore)))

    report.succeeded.shouldBe(true)
    cloudBackupDeleter.deletedStores.shouldContainExactly(CloudStore)
  }

  test("deletes all cloud backup stores") {
    val report = service().delete(listOf(AllCloudBackupStores))

    report.succeeded.shouldBe(true)
    cloudBackupDeleter.deleteAllCalls.shouldBe(1)
  }

  test("deletes active app spending key") {
    accountService.setActiveAccount(FullAccountMock)
    appPrivateKeyDao.storeAppSpendingKeyPair(AppSpendingKeypair)

    val report = service().delete(listOf(ActiveAppSpendingKey))

    report.succeeded.shouldBe(true)
    appPrivateKeyDao.appSpendingKeys.shouldNotContainKey(FullAccountMock.keybox.activeAppKeyBundle.spendingKey)
  }

  test("active account cloud backup fails without active account") {
    val report = service().delete(listOf(ActiveAccountCloudBackup))

    report.succeeded.shouldBe(false)
    report.failures.single().message.shouldBe("No active account.")
    cloudBackupDeleter.deletedAccountIds.shouldBeEmpty()
  }

  test("active account cloud backup uses account snapshot from start of deletion") {
    accountService.setActiveAccount(FullAccountMock)
    appDataDeleter.onDeleteAll = { accountService.clear() }

    val report = service().delete(listOf(AllLocalAppData, ActiveAccountCloudBackup))

    report.succeeded.shouldBe(true)
    cloudBackupDeleter.deletedAccountIds.shouldContainExactly(FullAccountMock.accountId)
  }

  test("corrupt cloud backup uses account snapshot from start of deletion") {
    accountService.setActiveAccount(FullAccountMock)
    appDataDeleter.onDeleteAll = { accountService.clear() }

    val report = service().delete(listOf(AllLocalAppData, CorruptCloudBackup))

    report.succeeded.shouldBe(true)
    cloudBackupCorrupter.corruptedAccountIds.shouldContainExactly(FullAccountMock.accountId)
  }

  test("corrupt cloud backup fails without active account") {
    val report = service().delete(listOf(CorruptCloudBackup))

    report.succeeded.shouldBe(false)
    report.failures.single().message.shouldBe("No active account.")
    cloudBackupCorrupter.corruptedAccountIds.shouldBeEmpty()
  }

  test("customer build rejects destructive deletion") {
    val report = service(Customer).delete(listOf(AllCloudBackupStores))

    report.succeeded.shouldBe(false)
    report.failures.single().target.shouldBe(AllCloudBackupStores)
    cloudBackupDeleter.deleteAllCalls.shouldBe(0)
  }
})

private class AppDataDeleterFake : AppDataDeleter {
  var deleteAllCalls = 0
  var onDeleteAll: suspend () -> Unit = {}

  override suspend fun deleteAll(): Result<Unit, Error> {
    deleteAllCalls += 1
    onDeleteAll()
    return Ok(Unit)
  }

  fun reset() {
    deleteAllCalls = 0
    onDeleteAll = {}
  }
}

private class CloudBackupDeleterFake : CloudBackupDeleter {
  val deletedAccountIds = mutableListOf<AccountId?>()
  val deletedStores = mutableListOf<CloudBackupStoreType>()
  var deleteAllCalls = 0

  override suspend fun delete(accountId: AccountId?): Result<Unit, Error> {
    deletedAccountIds += accountId
    return Ok(Unit)
  }

  override suspend fun deleteAllBackups(): Result<Unit, Error> {
    deleteAllCalls += 1
    return Ok(Unit)
  }

  override suspend fun deleteBackupsIn(type: CloudBackupStoreType): Result<Unit, Error> {
    deletedStores += type
    return Ok(Unit)
  }

  fun reset() {
    deletedAccountIds.clear()
    deletedStores.clear()
    deleteAllCalls = 0
  }
}

private class CloudBackupCorrupterFake : CloudBackupCorrupter {
  val corruptedAccountIds = mutableListOf<AccountId>()

  override suspend fun corrupt(accountId: AccountId): Result<Unit, CorruptionError> {
    corruptedAccountIds += accountId
    return Ok(Unit)
  }

  fun reset() {
    corruptedAccountIds.clear()
  }
}

private class CloudBackupKeysetDeleterFake : CloudBackupKeysetDeleter {
  var deleteActiveKeysetCalls = 0

  override suspend fun deleteActiveKeyset(): Result<Unit, KeysetDeletionError> {
    deleteActiveKeysetCalls += 1
    return Ok(Unit)
  }

  fun reset() {
    deleteActiveKeysetCalls = 0
  }
}
