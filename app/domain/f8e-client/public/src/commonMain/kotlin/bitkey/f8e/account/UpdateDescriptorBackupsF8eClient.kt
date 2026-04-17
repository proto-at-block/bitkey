package bitkey.f8e.account

import bitkey.backup.DescriptorBackup
import build.wallet.account.UpdateDescriptorBackupError
import build.wallet.bitkey.app.AppGlobalAuthKey
import build.wallet.bitkey.f8e.FullAccountId
import build.wallet.cloud.backup.csek.SealedSsek
import build.wallet.crypto.PublicKey
import build.wallet.f8e.F8eEnvironment
import build.wallet.f8e.auth.PrivilegedActionProof
import com.github.michaelbull.result.Result

interface UpdateDescriptorBackupsF8eClient {
  /**
   * Updates the set of descriptor backups as specified.
   *
   * @param proof Proof of privileged action. Can be null if the user is first onboarding, and
   * the descriptors are being uploaded for the first time.
   */
  suspend fun update(
    f8eEnvironment: F8eEnvironment,
    accountId: FullAccountId,
    descriptorBackups: List<DescriptorBackup>,
    sealedSsek: SealedSsek,
    appAuthKey: PublicKey<AppGlobalAuthKey>,
    proof: PrivilegedActionProof?,
  ): Result<Unit, UpdateDescriptorBackupError>
}
