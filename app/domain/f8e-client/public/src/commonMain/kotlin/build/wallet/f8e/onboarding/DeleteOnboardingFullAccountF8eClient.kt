package build.wallet.f8e.onboarding

import build.wallet.bitkey.f8e.FullAccountId
import build.wallet.f8e.F8eEnvironment
import build.wallet.f8e.auth.HwFactorProofOfPossession
import build.wallet.ktor.result.NetworkingError
import com.github.michaelbull.result.Result

/**
 * Deletes an onboarding full account. Will fail if the account already finished onboarding.
 * Succeeds if the account does not exist.
 */
interface DeleteOnboardingFullAccountF8eClient {
  suspend fun deleteOnboardingFullAccount(
    f8eEnvironment: F8eEnvironment,
    fullAccountId: FullAccountId,
    hwFactorProofOfPossession: HwFactorProofOfPossession,
  ): Result<Unit, NetworkingError>
}
