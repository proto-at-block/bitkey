package bitkey.onboarding

import build.wallet.bitkey.f8e.FullAccountId
import build.wallet.f8e.auth.HwFactorProofOfPossession
import com.github.michaelbull.result.Result

interface DeleteFullAccountService {
  /**
   * Deletes the given Full Account from server and local storage.
   */
  suspend fun deleteAccount(
    fullAccountId: FullAccountId,
    hardwareProofOfPossession: HwFactorProofOfPossession,
  ): Result<Unit, Error>
}
