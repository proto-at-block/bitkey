package build.wallet.cloud.backup

import bitkey.serialization.json.decodeFromStringResult
import build.wallet.cloud.backup.CloudBackupV2Restorer.CloudBackupV2RestorerError.PkekMissingError
import build.wallet.cloud.backup.CloudBackupV3Restorer.CloudBackupV3RestorerError
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

  val cloudBackupV2Restorer = CloudBackupV2RestorerMock()
  val cloudBackupV3Restorer = CloudBackupV3RestorerMock()

  val backupRestorer =
    FullAccountCloudBackupRestorerImpl(
      cloudBackupV2Restorer = cloudBackupV2Restorer,
      cloudBackupV3Restorer = cloudBackupV3Restorer
    )

  afterTest {
    cloudBackupV2Restorer.reset()
    cloudBackupV3Restorer.reset()
  }

  context("parameterized tests for all backup versions") {
    AllFullAccountBackupMocks.forEach { backup ->
      val backupVersion = when (backup) {
        is CloudBackupV2 -> "v2"
        is CloudBackupV3 -> "v3"
        else -> "unknown"
      }

      context("cloud backup $backupVersion") {
        test("success") {
          when (backup) {
            is CloudBackupV2 -> cloudBackupV2Restorer.result = Ok(AccountRestorationMock)
            is CloudBackupV3 -> cloudBackupV3Restorer.result = Ok(AccountRestorationMock)
            else -> error("Unknown backup version: $backup")
          }

          backupRestorer
            .restoreFromBackup(cloudBackup = backup)
            .shouldBe(Ok(AccountRestorationMock))
        }

        test("failure - CsekMissing") {
          when (backup) {
            is CloudBackupV2 -> cloudBackupV2Restorer.result = Err(PkekMissingError)
            is CloudBackupV3 ->
              cloudBackupV3Restorer.result =
                Err(CloudBackupV3RestorerError.PkekMissingError)
            else -> error("Unknown backup version: $backup")
          }

          backupRestorer
            .restoreFromBackup(cloudBackup = backup)
            .shouldBeErrOfType<CsekMissing>()
        }

        test("failure - AccountBackupDecodingError") {
          when (backup) {
            is CloudBackupV2 ->
              cloudBackupV2Restorer.result =
                Err(
                  CloudBackupV2Restorer.CloudBackupV2RestorerError.AccountBackupDecodingError(
                    cause = Throwable()
                  )
                )
            is CloudBackupV3 ->
              cloudBackupV3Restorer.result =
                Err(
                  CloudBackupV3RestorerError.AccountBackupDecodingError(
                    cause = Throwable()
                  )
                )
            else -> error("Unknown backup version: $backup")
          }

          backupRestorer
            .restoreFromBackup(cloudBackup = backup)
            .shouldBeErrOfType<AccountBackupDecodingError>()
        }

        test("failure - AccountBackupRestorationError via AppAuthKeypairStorageError") {
          when (backup) {
            is CloudBackupV2 ->
              cloudBackupV2Restorer.result =
                Err(
                  CloudBackupV2Restorer.CloudBackupV2RestorerError.AppAuthKeypairStorageError(
                    cause = Throwable()
                  )
                )
            is CloudBackupV3 ->
              cloudBackupV3Restorer.result =
                Err(
                  CloudBackupV3RestorerError.AppAuthKeypairStorageError(
                    cause = Throwable()
                  )
                )
            else -> error("Unknown backup version: $backup")
          }

          backupRestorer
            .restoreFromBackup(cloudBackup = backup)
            .shouldBeErrOfType<AccountBackupRestorationError>()
        }

        test("failure - AccountBackupRestorationError via AppSpendingKeypairStorageError") {
          when (backup) {
            is CloudBackupV2 ->
              cloudBackupV2Restorer.result =
                Err(
                  CloudBackupV2Restorer.CloudBackupV2RestorerError.AppSpendingKeypairStorageError(
                    cause = Throwable()
                  )
                )
            is CloudBackupV3 ->
              cloudBackupV3Restorer.result =
                Err(
                  CloudBackupV3RestorerError.AppSpendingKeypairStorageError(
                    cause = Throwable()
                  )
                )
            else -> error("Unknown backup version: $backup")
          }

          backupRestorer
            .restoreFromBackup(cloudBackup = backup)
            .shouldBeErrOfType<AccountBackupRestorationError>()
        }
      }
    }
  }

  context("cloud backup v2 - specific tests") {
    test("success - from json") {
      cloudBackupV2Restorer.result = Ok(AccountRestorationMock)
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
      cloudBackupV3Restorer.result = Ok(AccountRestorationMock)
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
