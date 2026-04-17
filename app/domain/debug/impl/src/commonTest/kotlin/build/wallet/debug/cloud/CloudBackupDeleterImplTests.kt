package build.wallet.debug.cloud

import build.wallet.bitkey.f8e.FullAccountIdMock
import build.wallet.cloud.backup.CloudBackupServiceFake
import build.wallet.cloud.backup.CloudBackupV3WithFullAccountMock
import build.wallet.cloud.backup.awaitNoBackups
import build.wallet.cloud.store.CloudAccountMock
import build.wallet.cloud.store.CloudStoreAccountError
import build.wallet.cloud.store.CloudStoreAccountRepositoryMock
import build.wallet.platform.config.AppVariant
import build.wallet.platform.config.AppVariant.Customer
import build.wallet.platform.config.AppVariant.Development
import build.wallet.platform.config.AppVariant.Team
import build.wallet.testing.shouldBeOk
import com.github.michaelbull.result.Err
import com.github.michaelbull.result.Ok
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.nulls.shouldBeNull

class CloudBackupDeleterImplTests : FunSpec({
  val accountId = FullAccountIdMock
  val cloudBackupService = CloudBackupServiceFake()
  val cloudBackupStoreCleaner = CloudBackupStoreCleanerFake()
  val cloudStoreAccountRepository = CloudStoreAccountRepositoryMock()
  val cloudAccount = CloudAccountMock("")
  val storeType = availableCloudBackupStoreTypes().first()

  fun cloudBackupDeleter(appVariant: AppVariant) =
    CloudBackupDeleterImpl(
      appVariant = appVariant,
      cloudBackupService = cloudBackupService,
      cloudBackupStoreCleaner = cloudBackupStoreCleaner,
      cloudStoreAccountRepository = cloudStoreAccountRepository
    )

  beforeTest {
    cloudStoreAccountRepository.currentAccountResult = Ok(cloudAccount)
    cloudBackupService.reset()
    cloudBackupStoreCleaner.reset()
  }

  context("Customer builds") {
    test("not allowed to delete single cloud backup") {
      shouldThrow<IllegalStateException> {
        cloudBackupDeleter(Customer).delete(accountId)
      }
    }

    test("not allowed to delete all cloud backups") {
      shouldThrow<IllegalStateException> {
        cloudBackupDeleter(Customer).deleteAllBackups()
      }
    }

    test("not allowed to delete cloud backups in a store") {
      shouldThrow<IllegalStateException> {
        cloudBackupDeleter(Customer).deleteBackupsIn(storeType)
      }
    }
  }

  listOf(Development, Team).forEach { variant ->
    context("$variant builds") {
      test("delete cloud backup for account id") {
        cloudBackupService.writeBackup(
          accountId = accountId,
          cloudStoreAccount = cloudAccount,
          backup = CloudBackupV3WithFullAccountMock,
          requireAuthRefresh = false
        ).shouldBeOk()

        cloudBackupDeleter(variant).delete(accountId)

        cloudBackupService.readActiveBackup(cloudAccount)
          .shouldBeOk()
          .shouldBeNull()
      }

      test("delete all cloud backups") {
        cloudBackupService.writeBackup(
          accountId = accountId,
          cloudStoreAccount = cloudAccount,
          backup = CloudBackupV3WithFullAccountMock,
          requireAuthRefresh = false
        ).shouldBeOk()

        cloudBackupDeleter(variant).deleteAllBackups()

        cloudBackupService.awaitNoBackups()
      }

      test("delete all cloud backups suppress the error when there is no cloud account") {
        cloudStoreAccountRepository.currentAccountResult = Err(object : CloudStoreAccountError() {})

        cloudBackupDeleter(variant).deleteAllBackups()
      }

      test("delete all cloud backups uses cloud backup service path") {
        cloudBackupDeleter(variant).deleteAllBackups()

        cloudBackupStoreCleaner.deleteCalls.shouldBeEmpty()
      }

      test("delete cloud backups in a store uses store cleaner path") {
        cloudBackupDeleter(variant).deleteBackupsIn(storeType)

        cloudBackupStoreCleaner.deleteCalls.shouldContain(
          CloudBackupStoreCleanerFake.DeleteCall(
            type = storeType,
            cloudStoreAccount = cloudAccount
          )
        )
      }
    }
  }
})
