package build.wallet.statemachine.data.recovery.inprogress

import build.wallet.bitkey.app.AppGlobalAuthPublicKey
import build.wallet.bitkey.app.AppSpendingPublicKey
import build.wallet.bitkey.f8e.F8eSpendingKeyset
import build.wallet.bitkey.f8e.F8eSpendingKeysetMock
import build.wallet.bitkey.f8e.FullAccountId
import build.wallet.bitkey.hardware.HwSpendingPublicKey
import build.wallet.bitkey.keybox.KeyboxConfig
import build.wallet.f8e.auth.HwFactorProofOfPossession
import com.github.michaelbull.result.Ok
import com.github.michaelbull.result.Result

class F8eSpendingKeyRotatorMock : F8eSpendingKeyRotator {
  override suspend fun rotateSpendingKey(
    keyboxConfig: KeyboxConfig,
    fullAccountId: FullAccountId,
    appAuthKey: AppGlobalAuthPublicKey,
    hardwareProofOfPossession: HwFactorProofOfPossession,
    appSpendingKey: AppSpendingPublicKey,
    hardwareSpendingKey: HwSpendingPublicKey,
  ): Result<F8eSpendingKeyset, Error> = Ok(F8eSpendingKeysetMock)
}