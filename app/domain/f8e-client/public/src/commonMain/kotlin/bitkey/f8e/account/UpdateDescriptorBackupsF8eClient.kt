package bitkey.f8e.account

import bitkey.backup.DescriptorBackup
import build.wallet.account.UpdateDescriptorBackupError
import build.wallet.bitkey.f8e.FullAccountId
import build.wallet.f8e.F8eEnvironment
import build.wallet.f8e.auth.HwFactorProofOfPossession
import com.github.michaelbull.result.Result

interface UpdateDescriptorBackupsF8eClient {
  /**
   * Updates the set of descriptor backups as specified.
   */
  suspend fun update(
    f8eEnvironment: F8eEnvironment,
    accountId: FullAccountId,
    descriptorBackups: List<DescriptorBackup>,
    hwKeyProof: HwFactorProofOfPossession,
  ): Result<Unit, UpdateDescriptorBackupError>
}
