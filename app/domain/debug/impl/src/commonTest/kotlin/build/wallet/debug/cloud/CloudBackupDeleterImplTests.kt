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
import build.wallet.testing.shouldBeErrOfType
import build.wallet.testing.shouldBeOk
import com.github.michaelbull.result.Err
import com.github.michaelbull.result.Ok
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe

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
      cloudBackupDeleter(Customer).delete(accountId)
        .shouldBeErrOfType<Error>()
        .message.shouldBe("Not allowed to clear cloud backups in Customer builds.")
    }

    test("not allowed to delete all cloud backups") {
      cloudBackupDeleter(Customer).deleteAllBackups()
        .shouldBeErrOfType<Error>()
        .message.shouldBe("Not allowed to clear cloud backups in Customer builds.")
    }

    test("not allowed to delete cloud backups in a store") {
      cloudBackupDeleter(Customer).deleteBackupsIn(storeType)
        .shouldBeErrOfType<Error>()
        .message.shouldBe("Not allowed to clear cloud backups in Customer builds.")
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

        cloudBackupDeleter(variant).delete(accountId).shouldBeOk()

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

        cloudBackupDeleter(variant).deleteAllBackups().shouldBeOk()

        cloudBackupService.awaitNoBackups()
      }

      test("delete all cloud backups fails when cloud account lookup fails") {
        cloudStoreAccountRepository.currentAccountResult = Err(object : CloudStoreAccountError() {})

        cloudBackupDeleter(variant).deleteAllBackups()
          .shouldBeErrOfType<Error>()
          .message.shouldBe("Failed to find cloud account")
      }

      test("delete all cloud backups fails when there is no cloud account") {
        cloudStoreAccountRepository.currentAccountResult = Ok(null)

        cloudBackupDeleter(variant).deleteAllBackups()
          .shouldBeErrOfType<Error>()
          .message.shouldBe("No cloud account")
      }

      test("delete all cloud backups uses cloud backup service path") {
        cloudBackupDeleter(variant).deleteAllBackups().shouldBeOk()

        cloudBackupStoreCleaner.deleteCalls.shouldBeEmpty()
      }

      test("delete cloud backups in a store uses store cleaner path") {
        cloudBackupDeleter(variant).deleteBackupsIn(storeType).shouldBeOk()

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
