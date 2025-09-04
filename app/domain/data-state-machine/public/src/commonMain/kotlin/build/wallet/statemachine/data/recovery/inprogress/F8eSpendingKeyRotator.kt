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
   * Creates a new f8e spending keyset without activating it.
   */
  suspend fun createSpendingKeyset(
    fullAccountId: FullAccountId,
    appAuthKey: PublicKey<AppGlobalAuthKey>,
    hardwareProofOfPossession: HwFactorProofOfPossession,
    appSpendingKey: AppSpendingPublicKey,
    hardwareSpendingKey: HwSpendingPublicKey,
  ): Result<F8eSpendingKeyset, Error>

  /**
   * Activates a previously created f8e spending keyset.
   * This is idempotent - calling it for an already active keyset will succeed.
   * Only one key can be activated per D&N
   */
  suspend fun activateSpendingKeyset(
    fullAccountId: FullAccountId,
    keyset: F8eSpendingKeyset,
    appAuthKey: PublicKey<AppGlobalAuthKey>,
    hardwareProofOfPossession: HwFactorProofOfPossession,
  ): Result<Unit, Error>
}
