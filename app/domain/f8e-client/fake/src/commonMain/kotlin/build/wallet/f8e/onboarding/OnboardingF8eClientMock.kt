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

  val completeOnboardingV2Calls = turbine("completeOnboardingV2 calls")
  var completeOnboardingV2Result: Result<CompleteOnboardingResponseV2, NetworkingError> =
    Ok(
      CompleteOnboardingResponseV2(
        appAuthPub = "",
        hardwareAuthPub = "",
        appSpendingPub = "",
        hardwareSpendingPub = "",
        serverSpendingPub = "",
        signature = ""
      )
    )

  override suspend fun completeOnboarding(
    f8eEnvironment: F8eEnvironment,
    fullAccountId: FullAccountId,
  ): Result<Unit, NetworkingError> {
    completeOnboardingCalls += Unit
    return completeOnboardingResult
  }

  override suspend fun completeOnboardingV2(
    f8eEnvironment: F8eEnvironment,
    fullAccountId: FullAccountId,
  ): Result<CompleteOnboardingResponseV2, NetworkingError> {
    completeOnboardingV2Calls += Unit
    return completeOnboardingV2Result
  }

  fun reset() {
    completeOnboardingResult = Ok(Unit)
    completeOnboardingV2Result =
      Ok(
        CompleteOnboardingResponseV2(
          appAuthPub = "",
          hardwareAuthPub = "",
          appSpendingPub = "",
          hardwareSpendingPub = "",
          serverSpendingPub = "",
          signature = ""
        )
      )
  }
}
