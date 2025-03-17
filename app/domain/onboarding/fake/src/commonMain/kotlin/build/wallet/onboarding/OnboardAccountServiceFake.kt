package build.wallet.onboarding

import com.github.michaelbull.result.Err
import com.github.michaelbull.result.Ok
import com.github.michaelbull.result.Result

class OnboardAccountServiceFake : OnboardAccountService {
  private val pendingSteps = mutableListOf<OnboardAccountStep?>(null)

  fun setPendingSteps(vararg steps: OnboardAccountStep) {
    pendingSteps.clear()
    pendingSteps += steps
  }

  override suspend fun pendingStep(): Result<OnboardAccountStep?, Throwable> {
    return Ok(pendingSteps.firstOrNull())
  }

  var completeStepError: Throwable? = null

  override suspend fun completeStep(step: OnboardAccountStep): Result<Unit, Throwable> {
    if (completeStepError != null) {
      return Err(completeStepError!!)
    }
    pendingSteps.remove(step)
    return Ok(Unit)
  }

  fun reset() {
    completeStepError = null
    pendingSteps.clear()
  }
}
