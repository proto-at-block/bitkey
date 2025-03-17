package build.wallet.statemachine.data.recovery.inprogress

import build.wallet.bitkey.app.AppGlobalAuthKey
import build.wallet.bitkey.app.AppSpendingPublicKey
import build.wallet.bitkey.f8e.F8eSpendingKeyset
import build.wallet.bitkey.f8e.FullAccountId
import build.wallet.bitkey.hardware.HwSpendingPublicKey
import build.wallet.crypto.PublicKey
import build.wallet.f8e.auth.HwFactorProofOfPossession
import com.github.michaelbull.result.Result

interface F8eSpendingKeyRotator {
  /**
   * Creates a new f8e spending key and activates it.
   */
  suspend fun rotateSpendingKey(
    fullAccountId: FullAccountId,
    appAuthKey: PublicKey<AppGlobalAuthKey>,
    hardwareProofOfPossession: HwFactorProofOfPossession,
    appSpendingKey: AppSpendingPublicKey,
    hardwareSpendingKey: HwSpendingPublicKey,
  ): Result<F8eSpendingKeyset, Error>
}
