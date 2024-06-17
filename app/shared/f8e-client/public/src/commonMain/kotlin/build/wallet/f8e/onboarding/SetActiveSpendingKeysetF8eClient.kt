package build.wallet.f8e.onboarding

import build.wallet.bitkey.app.AppGlobalAuthKey
import build.wallet.bitkey.f8e.FullAccountId
import build.wallet.crypto.PublicKey
import build.wallet.f8e.F8eEnvironment
import build.wallet.f8e.auth.HwFactorProofOfPossession
import build.wallet.ktor.result.NetworkingError
import com.github.michaelbull.result.Result

interface SetActiveSpendingKeysetF8eClient {
  /**
   * Set active spending f8e dpub.
   *
   * @param fullAccountId current account ID.
   * @param keysetId f8e keyset ID to set as active.
   * @param hwFactorProofOfPossession proof of possession of hardware factor, used by f8e to allow
   * active keyset rotation.
   */
  suspend fun set(
    f8eEnvironment: F8eEnvironment,
    fullAccountId: FullAccountId,
    keysetId: String,
    appAuthKey: PublicKey<AppGlobalAuthKey>,
    hwFactorProofOfPossession: HwFactorProofOfPossession,
  ): Result<Unit, NetworkingError>
}
