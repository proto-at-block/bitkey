package build.wallet.f8e.onboarding

import build.wallet.bitkey.app.AppGlobalAuthKey
import build.wallet.bitkey.f8e.FullAccountId
import build.wallet.crypto.PublicKey
import build.wallet.f8e.F8eEnvironment
import build.wallet.f8e.auth.PrivilegedActionProof
import build.wallet.f8e.recovery.SignedKeysetVerificationResponse
import build.wallet.ktor.result.NetworkingError
import com.github.michaelbull.result.Result

interface SetActiveSpendingKeysetF8eClient {
  /**
   * Set active spending f8e dpub.
   *
   * @param fullAccountId current account ID.
   * @param keysetId f8e keyset ID to set as active.
   * @param proof proof of privileged action, used by f8e to allow active keyset rotation.
   * @return For W3 accounts, returns signed keyset verification data. For W1 accounts, returns null.
   */
  suspend fun set(
    f8eEnvironment: F8eEnvironment,
    fullAccountId: FullAccountId,
    keysetId: String,
    appAuthKey: PublicKey<AppGlobalAuthKey>,
    proof: PrivilegedActionProof,
  ): Result<SignedKeysetVerificationResponse?, NetworkingError>
}
