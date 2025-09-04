package build.wallet.statemachine.data.recovery.inprogress

import bitkey.recovery.RecoveryStatusService
import build.wallet.bitkey.app.AppGlobalAuthKey
import build.wallet.bitkey.app.AppSpendingPublicKey
import build.wallet.bitkey.f8e.F8eSpendingKeyset
import build.wallet.bitkey.f8e.F8eSpendingKeysetMock
import build.wallet.bitkey.f8e.FullAccountId
import build.wallet.bitkey.hardware.HwSpendingPublicKey
import build.wallet.crypto.PublicKey
import build.wallet.f8e.auth.HwFactorProofOfPossession
import build.wallet.recovery.LocalRecoveryAttemptProgress.ActivatedSpendingKeys
import com.github.michaelbull.result.Ok
import com.github.michaelbull.result.Result

class F8eSpendingKeyRotatorMock(
  val recoveryStatusService: RecoveryStatusService,
) : F8eSpendingKeyRotator {
  var createSpendingKeysetResult: Result<F8eSpendingKeyset, Error> = Ok(F8eSpendingKeysetMock)
  var activateSpendingKeysetResult: Result<Unit, Error> = Ok(Unit)

  override suspend fun createSpendingKeyset(
    fullAccountId: FullAccountId,
    appAuthKey: PublicKey<AppGlobalAuthKey>,
    hardwareProofOfPossession: HwFactorProofOfPossession,
    appSpendingKey: AppSpendingPublicKey,
    hardwareSpendingKey: HwSpendingPublicKey,
  ): Result<F8eSpendingKeyset, Error> = createSpendingKeysetResult

  override suspend fun activateSpendingKeyset(
    fullAccountId: FullAccountId,
    keyset: F8eSpendingKeyset,
    appAuthKey: PublicKey<AppGlobalAuthKey>,
    hardwareProofOfPossession: HwFactorProofOfPossession,
  ): Result<Unit, Error> =
    activateSpendingKeysetResult.also {
      recoveryStatusService.setLocalRecoveryProgress(ActivatedSpendingKeys(keyset))
    }

  fun reset() {
    createSpendingKeysetResult = Ok(F8eSpendingKeysetMock)
    activateSpendingKeysetResult = Ok(Unit)
  }
}
