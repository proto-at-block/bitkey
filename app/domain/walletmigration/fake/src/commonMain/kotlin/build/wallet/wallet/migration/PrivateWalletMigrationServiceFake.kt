package build.wallet.wallet.migration

import build.wallet.bitkey.account.FullAccount
import build.wallet.bitkey.hardware.HwKeyBundle
import build.wallet.bitkey.keybox.Keybox
import build.wallet.cloud.backup.csek.SealedSsek
import build.wallet.cloud.backup.csek.Sek
import build.wallet.f8e.auth.HwFactorProofOfPossession
import build.wallet.money.BitcoinMoney
import com.github.michaelbull.result.Err
import com.github.michaelbull.result.Ok
import com.github.michaelbull.result.Result
import kotlinx.coroutines.flow.MutableStateFlow

class PrivateWalletMigrationServiceFake : PrivateWalletMigrationService {
  var initiateMigrationResult: Result<PrivateWalletMigrationState.InitiationComplete, PrivateWalletMigrationError> =
    Err(PrivateWalletMigrationError.FeatureNotAvailable)
  var estimateMigrationFeesResult: Result<BitcoinMoney, PrivateWalletMigrationError> =
    Ok(BitcoinMoney.sats(1000))
  var completeMigrationResult: Result<Unit, PrivateWalletMigrationError> = Ok(Unit)
  var finalizeMigrationResult: Result<Keybox, PrivateWalletMigrationError> =
    Err(PrivateWalletMigrationError.FeatureNotAvailable)
  var cancelMigrationResult: Result<Unit, PrivateWalletMigrationError> = Ok(Unit)
  var completeMigrationCallCount = 0
  override val migrationState = MutableStateFlow<PrivateWalletMigrationState>(PrivateWalletMigrationState.Available)

  override suspend fun initiateMigration(
    account: FullAccount,
    proofOfPossession: HwFactorProofOfPossession,
    newHwKeys: HwKeyBundle,
    ssek: Sek,
    sealedSsek: SealedSsek,
  ): Result<PrivateWalletMigrationState.InitiationComplete, PrivateWalletMigrationError> {
    return initiateMigrationResult
  }

  override suspend fun estimateMigrationFees(
    account: FullAccount,
  ): Result<BitcoinMoney, PrivateWalletMigrationError> {
    return estimateMigrationFeesResult
  }

  override suspend fun completeCloudBackup(): Result<Unit, PrivateWalletMigrationError> {
    return Ok(Unit)
  }

  override suspend fun completeMigration(): Result<Unit, PrivateWalletMigrationError> {
    completeMigrationCallCount++
    return completeMigrationResult
  }

  override suspend fun clearMigration() {
    reset()
  }

  fun reset() {
    initiateMigrationResult = Err(PrivateWalletMigrationError.FeatureNotAvailable)
    estimateMigrationFeesResult = Ok(BitcoinMoney.sats(1000))
    completeMigrationResult = Ok(Unit)
    completeMigrationCallCount = 0
    finalizeMigrationResult = Err(PrivateWalletMigrationError.FeatureNotAvailable)
    cancelMigrationResult = Ok(Unit)
    migrationState.value = PrivateWalletMigrationState.Available
  }
}
