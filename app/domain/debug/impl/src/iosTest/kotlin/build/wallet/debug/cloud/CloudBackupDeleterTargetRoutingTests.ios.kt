package build.wallet.debug.cloud

import build.wallet.bitkey.f8e.FullAccountIdMock
import build.wallet.cloud.backup.CloudBackupServiceFake
import build.wallet.cloud.backup.CloudBackupV3WithFullAccountMock
import build.wallet.cloud.store.CloudAccountMock
import build.wallet.cloud.store.CloudStoreAccountRepositoryMock
import build.wallet.platform.config.AppVariant.Development
import build.wallet.testing.shouldBeOk
import com.github.michaelbull.result.Ok
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.nulls.shouldNotBeNull

class CloudBackupDeleterTargetRoutingTests : FunSpec({
  val cloudBackupService = CloudBackupServiceFake()
  val cloudBackupStoreCleaner = CloudBackupStoreCleanerFake()
  val cloudStoreAccountRepository = CloudStoreAccountRepositoryMock()
  val cloudAccount = CloudAccountMock("ios-account")

  val cloudBackupDeleter =
    CloudBackupDeleterImpl(
      appVariant = Development,
      cloudBackupService = cloudBackupService,
      cloudBackupStoreCleaner = cloudBackupStoreCleaner,
      cloudStoreAccountRepository = cloudStoreAccountRepository
    )

  beforeTest {
    cloudStoreAccountRepository.currentAccountResult = Ok(cloudAccount)
    cloudBackupService.reset()
    cloudBackupStoreCleaner.reset()
  }

  test("targeted deletion routes through store cleaner") {
    cloudBackupService.writeBackup(
      accountId = FullAccountIdMock,
      cloudStoreAccount = cloudAccount,
      backup = CloudBackupV3WithFullAccountMock,
      requireAuthRefresh = false
    ).shouldBeOk()

    cloudBackupDeleter.deleteBackupsIn(UbiquitousKvs).shouldBeOk()

    cloudBackupStoreCleaner.deleteCalls.shouldContain(
      CloudBackupStoreCleanerFake.DeleteCall(
        type = UbiquitousKvs,
        cloudStoreAccount = cloudAccount
      )
    )

    cloudBackupService.readActiveBackup(cloudAccount)
      .shouldBeOk()
      .shouldNotBeNull()
  }
})
