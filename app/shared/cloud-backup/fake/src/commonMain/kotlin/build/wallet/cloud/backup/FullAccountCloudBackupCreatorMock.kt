package build.wallet.cloud.backup

import app.cash.turbine.Turbine
import app.cash.turbine.plusAssign
import build.wallet.bitkey.keybox.Keybox
import build.wallet.bitkey.socrec.TrustedContact
import build.wallet.cloud.backup.FullAccountCloudBackupCreator.FullAccountCloudBackupCreatorError
import build.wallet.cloud.backup.csek.SealedCsek
import com.github.michaelbull.result.Result

class FullAccountCloudBackupCreatorMock(
  turbine: (String) -> Turbine<Any?>,
) : FullAccountCloudBackupCreator {
  var backupResult: Result<CloudBackup, FullAccountCloudBackupCreatorError>? = null
  val createCalls = turbine("create calls")

  override suspend fun create(
    keybox: Keybox,
    sealedCsek: SealedCsek,
    trustedContacts: List<TrustedContact>,
  ): Result<CloudBackup, FullAccountCloudBackupCreatorError> {
    createCalls += sealedCsek to keybox
    return backupResult!!
  }

  fun reset() {
    backupResult = null
  }
}
