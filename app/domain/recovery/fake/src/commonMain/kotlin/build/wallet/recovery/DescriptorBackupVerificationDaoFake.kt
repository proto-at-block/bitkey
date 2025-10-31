package build.wallet.recovery

import bitkey.recovery.DescriptorBackupVerificationDao
import bitkey.recovery.VerifiedBackup
import com.github.michaelbull.result.Ok
import com.github.michaelbull.result.Result

class DescriptorBackupVerificationDaoFake : DescriptorBackupVerificationDao {
  private var verifiedBackups = mutableMapOf<String, VerifiedBackup>()

  override suspend fun getVerifiedBackup(keysetId: String): Result<VerifiedBackup?, Error> =
    Ok(verifiedBackups[keysetId])

  override suspend fun replaceAllVerifiedBackups(
    backups: List<VerifiedBackup>,
  ): Result<Unit, Error> {
    verifiedBackups.clear()
    backups.forEach { backup ->
      verifiedBackups[backup.keysetId] = backup
    }
    return Ok(Unit)
  }

  override suspend fun clear(): Result<Unit, Error> {
    verifiedBackups.clear()
    return Ok(Unit)
  }

  fun reset() {
    verifiedBackups.clear()
  }
}
