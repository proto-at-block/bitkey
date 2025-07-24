package bitkey.f8e.account

import bitkey.backup.DescriptorBackup
import build.wallet.account.UpdateDescriptorBackupError
import build.wallet.bitkey.app.AppGlobalAuthKey
import build.wallet.bitkey.f8e.FullAccountId
import build.wallet.cloud.backup.csek.SealedSsek
import build.wallet.crypto.PublicKey
import build.wallet.f8e.F8eEnvironment
import build.wallet.f8e.auth.HwFactorProofOfPossession
import com.github.michaelbull.result.Ok
import com.github.michaelbull.result.Result

class UpdateDescriptorBackupsF8eClientFake(
  var updateResult: Result<Unit, UpdateDescriptorBackupError> = Ok(Unit),
) : UpdateDescriptorBackupsF8eClient {
  override suspend fun update(
    f8eEnvironment: F8eEnvironment,
    accountId: FullAccountId,
    descriptorBackups: List<DescriptorBackup>,
    sealedSsek: SealedSsek,
    appAuthKey: PublicKey<AppGlobalAuthKey>,
    hwKeyProof: HwFactorProofOfPossession?,
  ): Result<Unit, UpdateDescriptorBackupError> {
    return updateResult
  }

  fun reset() {
    updateResult = Ok(Unit)
  }
}
