package build.wallet.wallet.migration

import build.wallet.bitkey.account.FullAccount
import build.wallet.bitkey.hardware.HwKeyBundle
import build.wallet.bitkey.keybox.Keybox
import build.wallet.bitkey.spending.SpendingKeyset
import build.wallet.f8e.auth.HwFactorProofOfPossession
import com.github.michaelbull.result.Err
import com.github.michaelbull.result.Ok
import com.github.michaelbull.result.Result
import kotlinx.coroutines.flow.MutableStateFlow

class PrivateWalletMigrationServiceFake : PrivateWalletMigrationService {
  var initiateMigrationResult: Result<SpendingKeyset, PrivateWalletMigrationError> =
    Err(PrivateWalletMigrationError.FeatureNotAvailable)
  var prepareSweepResult: Result<PrivateWalletSweep?, PrivateWalletMigrationError> =
    Err(PrivateWalletMigrationError.FeatureNotAvailable)
  var finalizeMigrationResult: Result<Keybox, PrivateWalletMigrationError> =
    Err(PrivateWalletMigrationError.FeatureNotAvailable)
  var cancelMigrationResult: Result<Unit, PrivateWalletMigrationError> = Ok(Unit)
  override val isPrivateWalletMigrationAvailable = MutableStateFlow(true)

  override suspend fun initiateMigration(
    account: FullAccount,
    proofOfPossession: HwFactorProofOfPossession,
    newHwKeys: HwKeyBundle,
  ): Result<SpendingKeyset, PrivateWalletMigrationError> {
    return initiateMigrationResult
  }

  override suspend fun prepareSweep(
    account: FullAccount,
  ): Result<PrivateWalletSweep?, PrivateWalletMigrationError> {
    return prepareSweepResult
  }

  override suspend fun finalizeMigration(
    sweepTxId: String,
    account: FullAccount,
  ): Result<Keybox, PrivateWalletMigrationError> {
    return finalizeMigrationResult
  }

  override suspend fun cancelMigration(
    account: FullAccount,
  ): Result<Unit, PrivateWalletMigrationError> {
    return cancelMigrationResult
  }

  fun reset() {
    initiateMigrationResult = Err(PrivateWalletMigrationError.FeatureNotAvailable)
    prepareSweepResult = Err(PrivateWalletMigrationError.FeatureNotAvailable)
    finalizeMigrationResult = Err(PrivateWalletMigrationError.FeatureNotAvailable)
    cancelMigrationResult = Ok(Unit)
    isPrivateWalletMigrationAvailable.value = true
  }
}
