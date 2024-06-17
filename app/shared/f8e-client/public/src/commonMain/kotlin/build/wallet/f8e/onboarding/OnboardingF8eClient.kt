package build.wallet.f8e.onboarding

import build.wallet.bitkey.f8e.FullAccountId
import build.wallet.f8e.F8eEnvironment
import build.wallet.ktor.result.NetworkingError
import com.github.michaelbull.result.Result

interface OnboardingF8eClient {
  /**
   * Called to notify the server that onboarding has been completed.
   *
   * When the server receives this call, it will start requiring [HwProofOfPossession] for
   * certain protected customer actions (like setting up notifications). This allows actions
   * taken during onboarding (before this call is made) to be performed without needing to
   * provide [HwProofOfPossession] (for a better customer experience).
   */
  suspend fun completeOnboarding(
    f8eEnvironment: F8eEnvironment,
    fullAccountId: FullAccountId,
  ): Result<Unit, NetworkingError>
}
