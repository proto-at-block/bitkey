package build.wallet.f8e.onboarding

import app.cash.turbine.Turbine
import app.cash.turbine.plusAssign
import build.wallet.bitkey.f8e.FullAccountId
import build.wallet.f8e.F8eEnvironment
import build.wallet.ktor.result.NetworkingError
import com.github.michaelbull.result.Ok
import com.github.michaelbull.result.Result

class OnboardingF8eClientMock(
  turbine: (String) -> Turbine<Any>,
) : OnboardingF8eClient {
  val completeOnboardingCalls = turbine("completeOnboarding calls")
  var completeOnboardingResult: Result<Unit, NetworkingError> = Ok(Unit)

  override suspend fun completeOnboarding(
    f8eEnvironment: F8eEnvironment,
    fullAccountId: FullAccountId,
  ): Result<Unit, NetworkingError> {
    completeOnboardingCalls += Unit
    return completeOnboardingResult
  }

  fun reset() {
    completeOnboardingResult = Ok(Unit)
  }
}
