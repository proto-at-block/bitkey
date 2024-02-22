package build.wallet.auth

import build.wallet.bitkey.f8e.FullAccountId
import build.wallet.f8e.F8eEnvironment
import build.wallet.f8e.auth.HwFactorProofOfPossession
import com.github.michaelbull.result.Result

interface OnboardingFullAccountDeleter {
  /**
   * Deletes the given Full Account from server and local storage.
   */
  suspend fun deleteAccount(
    fullAccountId: FullAccountId,
    f8eEnvironment: F8eEnvironment,
    hardwareProofOfPossession: HwFactorProofOfPossession,
  ): Result<Unit, Error>
}
