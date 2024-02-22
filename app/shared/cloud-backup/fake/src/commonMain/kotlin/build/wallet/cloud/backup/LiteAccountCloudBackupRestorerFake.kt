package build.wallet.cloud.backup

import app.cash.turbine.Turbine
import app.cash.turbine.plusAssign
import build.wallet.bitkey.account.LiteAccount
import build.wallet.bitkey.keybox.LiteAccountMock
import build.wallet.cloud.backup.RestoreFromBackupError.AccountBackupRestorationError
import com.github.michaelbull.result.Err
import com.github.michaelbull.result.Ok
import com.github.michaelbull.result.Result

class LiteAccountCloudBackupRestorerFake(
  turbine: (String) -> Turbine<Any>,
) : LiteAccountCloudBackupRestorer {
  val restoreFromBackupCalls = turbine("restore from backup calls")
  var returnError: Err<AccountBackupRestorationError>? = null

  override suspend fun restoreFromBackup(
    liteAccountCloudBackup: CloudBackupV2,
  ): Result<LiteAccount, AccountBackupRestorationError> {
    restoreFromBackupCalls += liteAccountCloudBackup
    return returnError ?: Ok(LiteAccountMock)
  }

  fun reset() {
    returnError = null
  }
}
