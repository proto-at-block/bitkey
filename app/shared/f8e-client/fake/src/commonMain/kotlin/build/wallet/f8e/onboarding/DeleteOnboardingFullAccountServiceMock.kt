package build.wallet.f8e.onboarding

import app.cash.turbine.Turbine
import build.wallet.bitkey.f8e.FullAccountId
import build.wallet.f8e.F8eEnvironment
import build.wallet.f8e.auth.HwFactorProofOfPossession
import build.wallet.ktor.result.NetworkingError
import com.github.michaelbull.result.Ok
import com.github.michaelbull.result.Result

class DeleteOnboardingFullAccountServiceMock(
  turbine: (String) -> Turbine<Any>,
) : DeleteOnboardingFullAccountService {
  val deleteCalls = turbine("delete lite account calls")

  override suspend fun deleteOnboardingFullAccount(
    f8eEnvironment: F8eEnvironment,
    fullAccountId: FullAccountId,
    hwFactorProofOfPossession: HwFactorProofOfPossession,
  ): Result<Unit, NetworkingError> {
    deleteCalls.add(fullAccountId)
    return Ok(Unit)
  }
}
