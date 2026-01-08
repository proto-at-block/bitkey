package build.wallet.debug.cloud

import build.wallet.bitkey.f8e.FullAccountIdMock
import build.wallet.cloud.backup.CloudBackupRepositoryFake
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
import io.kotest.matchers.nulls.shouldBeNull

class CloudBackupDeleterImplTests : FunSpec({
  val accountId = FullAccountIdMock
  val cloudBackupRepository = CloudBackupRepositoryFake()
  val cloudStoreAccountRepository = CloudStoreAccountRepositoryMock()
  val cloudAccount = CloudAccountMock("")

  fun cloudBackupDeleter(appVariant: AppVariant) =
    CloudBackupDeleterImpl(
      appVariant = appVariant,
      cloudBackupRepository = cloudBackupRepository,
      cloudStoreAccountRepository = cloudStoreAccountRepository
    )

  beforeTest {
    cloudStoreAccountRepository.currentAccountResult = Ok(cloudAccount)
    cloudBackupRepository.reset()
  }

  context("Customer builds") {
    test("not allowed to delete single cloud backup") {
      shouldThrow<IllegalStateException> {
        cloudBackupDeleter(Customer).delete(accountId)
      }
    }

    test("not allowed to delete all cloud backups") {
      shouldThrow<IllegalStateException> {
        cloudBackupDeleter(Customer).deleteAll()
      }
    }
  }

  listOf(Development, Team).forEach { variant ->
    context("$variant builds") {
      test("delete cloud backup for account id") {
        cloudBackupRepository.writeBackup(
          accountId = accountId,
          cloudStoreAccount = cloudAccount,
          backup = CloudBackupV3WithFullAccountMock,
          requireAuthRefresh = false
        ).shouldBeOk()

        cloudBackupDeleter(variant).delete(accountId)

        cloudBackupRepository.readActiveBackup(cloudAccount)
          .shouldBeOk()
          .shouldBeNull()
      }

      test("delete all cloud backups") {
        cloudBackupRepository.writeBackup(
          accountId = accountId,
          cloudStoreAccount = cloudAccount,
          backup = CloudBackupV3WithFullAccountMock,
          requireAuthRefresh = false
        ).shouldBeOk()

        cloudBackupDeleter(variant).deleteAll()

        cloudBackupRepository.awaitNoBackups()
      }

      test("delete all cloud backups suppress the error when there is no cloud account") {
        cloudStoreAccountRepository.currentAccountResult = Err(object : CloudStoreAccountError() {})

        cloudBackupDeleter(variant).deleteAll()
      }
    }
  }
})
