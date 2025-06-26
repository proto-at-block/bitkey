package bitkey.recovery

import build.wallet.bitkey.app.AppSpendingPublicKey
import build.wallet.bitkey.f8e.F8eSpendingKeyset
import build.wallet.bitkey.f8e.FullAccountId
import build.wallet.bitkey.factor.PhysicalFactor
import build.wallet.bitkey.hardware.HwSpendingPublicKey
import com.github.michaelbull.result.Result

class DescriptorBackupServiceImpl : DescriptorBackupService {
  override suspend fun prepareDescriptorBackupsForRecovery(
    accountId: FullAccountId,
    factorToRecover: PhysicalFactor,
    f8eSpendingKeyset: F8eSpendingKeyset,
    appSpendingKey: AppSpendingPublicKey,
    hwSpendingKey: HwSpendingPublicKey,
  ): Result<DescriptorBackupPreparedData, Error> {
    TODO("Not yet implemented")
  }
}
