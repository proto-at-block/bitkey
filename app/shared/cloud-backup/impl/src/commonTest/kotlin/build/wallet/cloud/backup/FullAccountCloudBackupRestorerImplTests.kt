package build.wallet.cloud.backup

import build.wallet.cloud.backup.CloudBackupV2Restorer.CloudBackupV2RestorerError.PkekMissingError
import build.wallet.cloud.backup.RestoreFromBackupError.AccountBackupDecodingError
import build.wallet.cloud.backup.RestoreFromBackupError.AccountBackupRestorationError
import build.wallet.cloud.backup.RestoreFromBackupError.CsekMissing
import build.wallet.serialization.json.decodeFromStringResult
import build.wallet.testing.shouldBeErrOfType
import com.github.michaelbull.result.Err
import com.github.michaelbull.result.Ok
import com.github.michaelbull.result.getOrThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import kotlinx.serialization.json.Json

class FullAccountCloudBackupRestorerImplTests : FunSpec({

  val cloudBackupV2Restorer = CloudBackupV2RestorerMock()

  val backupRestorer =
    FullAccountCloudBackupRestorerImpl(
      cloudBackupV2Restorer = cloudBackupV2Restorer
    )

  afterTest {
    cloudBackupV2Restorer.reset()
  }

  context("cloud backup v2") {
    test("success") {
      cloudBackupV2Restorer.result = Ok(AccountRestorationMock)
      backupRestorer
        .restoreFromBackup(cloudBackup = CloudBackupV2WithFullAccountMock)
        .shouldBe(Ok(AccountRestorationMock))
    }

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

    test("failure - CsekMissing") {
      cloudBackupV2Restorer.result = Err(PkekMissingError)
      backupRestorer
        .restoreFromBackup(cloudBackup = CloudBackupV2WithFullAccountMock)
        .shouldBeErrOfType<CsekMissing>()
    }

    test("failure - AccountBackupDecodingError") {
      cloudBackupV2Restorer.result =
        Err(
          CloudBackupV2Restorer.CloudBackupV2RestorerError.AccountBackupDecodingError(
            cause = Throwable()
          )
        )

      backupRestorer
        .restoreFromBackup(cloudBackup = CloudBackupV2WithFullAccountMock)
        .shouldBeErrOfType<AccountBackupDecodingError>()
    }

    test("failure - AccountBackupRestorationError via AppAuthKeypairStorageError") {
      cloudBackupV2Restorer.result =
        Err(
          CloudBackupV2Restorer.CloudBackupV2RestorerError.AppAuthKeypairStorageError(
            cause = Throwable()
          )
        )

      backupRestorer
        .restoreFromBackup(cloudBackup = CloudBackupV2WithFullAccountMock)
        .shouldBeErrOfType<AccountBackupRestorationError>()
    }

    test("failure - AccountBackupRestorationError via AppSpendingKeypairStorageError") {
      cloudBackupV2Restorer.result =
        Err(
          CloudBackupV2Restorer.CloudBackupV2RestorerError.AppSpendingKeypairStorageError(
            cause = Throwable()
          )
        )

      backupRestorer
        .restoreFromBackup(cloudBackup = CloudBackupV2WithFullAccountMock)
        .shouldBeErrOfType<AccountBackupRestorationError>()
    }
  }
})
