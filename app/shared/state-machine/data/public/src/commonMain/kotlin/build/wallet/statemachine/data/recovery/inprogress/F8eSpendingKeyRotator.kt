package build.wallet.statemachine.data.recovery.inprogress

import build.wallet.bitkey.account.FullAccountConfig
import build.wallet.bitkey.app.AppGlobalAuthPublicKey
import build.wallet.bitkey.app.AppSpendingPublicKey
import build.wallet.bitkey.f8e.F8eSpendingKeyset
import build.wallet.bitkey.f8e.FullAccountId
import build.wallet.bitkey.hardware.HwSpendingPublicKey
import build.wallet.f8e.auth.HwFactorProofOfPossession
import com.github.michaelbull.result.Result

interface F8eSpendingKeyRotator {
  /**
   * Creates a new f8e spending key and activates it.
   */
  suspend fun rotateSpendingKey(
    fullAccountConfig: FullAccountConfig,
    fullAccountId: FullAccountId,
    appAuthKey: AppGlobalAuthPublicKey,
    hardwareProofOfPossession: HwFactorProofOfPossession,
    appSpendingKey: AppSpendingPublicKey,
    hardwareSpendingKey: HwSpendingPublicKey,
  ): Result<F8eSpendingKeyset, Error>
}
