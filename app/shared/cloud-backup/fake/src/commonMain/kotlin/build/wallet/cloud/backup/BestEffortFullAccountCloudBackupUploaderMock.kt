package build.wallet.cloud.backup

import app.cash.turbine.Turbine
import app.cash.turbine.plusAssign
import build.wallet.bitkey.account.FullAccount
import build.wallet.cloud.backup.BestEffortFullAccountCloudBackupUploader.Failure
import com.github.michaelbull.result.Ok
import com.github.michaelbull.result.Result

class BestEffortFullAccountCloudBackupUploaderMock(
  turbine: (String) -> Turbine<Any?>,
) : BestEffortFullAccountCloudBackupUploader {
  val createAndUploadCloudBackupCalls =
    turbine("create and upload cloud backup ignoring errors calls BestEffortFullAccountCloudBackupUploaderMock")

  var createAndUploadCloudBackupResult: Result<Unit, Failure> = Ok(Unit)

  fun reset() {
    createAndUploadCloudBackupResult = Ok(Unit)
  }

  override suspend fun createAndUploadCloudBackup(
    fullAccount: FullAccount,
  ): Result<Unit, Failure> {
    createAndUploadCloudBackupCalls += fullAccount
    return createAndUploadCloudBackupResult
  }
}
