package build.wallet.cloud.backup

import bitkey.serialization.json.decodeFromStringResult
import build.wallet.cloud.backup.CloudBackupRestorer.CloudBackupRestorerError
import build.wallet.cloud.backup.RestoreFromBackupError.AccountBackupDecodingError
import build.wallet.cloud.backup.RestoreFromBackupError.AccountBackupRestorationError
import build.wallet.cloud.backup.RestoreFromBackupError.CsekMissing
import build.wallet.testing.shouldBeErrOfType
import com.github.michaelbull.result.Err
import com.github.michaelbull.result.Ok
import com.github.michaelbull.result.getOrThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import kotlinx.serialization.json.Json

class FullAccountCloudBackupRestorerImplTests : FunSpec({

  val cloudBackupRestorer = CloudBackupRestorerMock()

  val backupRestorer =
    FullAccountCloudBackupRestorerImpl(
      cloudBackupRestorer = cloudBackupRestorer
    )

  afterTest {
    cloudBackupRestorer.reset()
  }

  context("parameterized tests for all backup versions") {
    AllFullAccountBackupMocks.forEach { backup ->
      val backupVersion = when (backup) {
        is CloudBackupV2 -> "v2"
        is CloudBackupV3 -> "v3"
      }

      context("cloud backup $backupVersion") {
        test("success") {
          cloudBackupRestorer.result = Ok(AccountRestorationMock)

          backupRestorer
            .restoreFromBackup(cloudBackup = backup)
            .shouldBe(Ok(AccountRestorationMock))
        }

        test("failure - CsekMissing") {
          cloudBackupRestorer.result = Err(CloudBackupRestorerError.PkekMissingError)

          backupRestorer
            .restoreFromBackup(cloudBackup = backup)
            .shouldBeErrOfType<CsekMissing>()
        }

        test("failure - AccountBackupDecodingError") {
          cloudBackupRestorer.result =
            Err(CloudBackupRestorerError.AccountBackupDecodingError(cause = Throwable()))

          backupRestorer
            .restoreFromBackup(cloudBackup = backup)
            .shouldBeErrOfType<AccountBackupDecodingError>()
        }

        test("failure - AccountBackupRestorationError via AppAuthKeypairStorageError") {
          cloudBackupRestorer.result =
            Err(CloudBackupRestorerError.AppAuthKeypairStorageError(cause = Throwable()))

          backupRestorer
            .restoreFromBackup(cloudBackup = backup)
            .shouldBeErrOfType<AccountBackupRestorationError>()
        }

        test("failure - AccountBackupRestorationError via AppSpendingKeypairStorageError") {
          cloudBackupRestorer.result =
            Err(CloudBackupRestorerError.AppSpendingKeypairStorageError(cause = Throwable()))

          backupRestorer
            .restoreFromBackup(cloudBackup = backup)
            .shouldBeErrOfType<AccountBackupRestorationError>()
        }
      }
    }
  }

  context("cloud backup v2 - specific tests") {
    test("success - from json") {
      cloudBackupRestorer.result = Ok(AccountRestorationMock)
      val backup =
        Json.decodeFromStringResult<CloudBackupV2>(
          CLOUD_BACKUP_V2_WITH_FULL_ACCOUNT_FIELDS_JSON
        ).getOrThrow()

      backupRestorer
        .restoreFromBackup(cloudBackup = backup)
        .shouldBe(Ok(AccountRestorationMock))
    }
  }

  context("cloud backup v3 - specific tests") {
    test("success - from json") {
      cloudBackupRestorer.result = Ok(AccountRestorationMock)
      val backup =
        Json.decodeFromStringResult<CloudBackupV3>(
          CLOUD_BACKUP_V3_WITH_FULL_ACCOUNT_FIELDS_JSON
        ).getOrThrow()

      backupRestorer
        .restoreFromBackup(cloudBackup = backup)
        .shouldBe(Ok(AccountRestorationMock))
    }
  }
})
