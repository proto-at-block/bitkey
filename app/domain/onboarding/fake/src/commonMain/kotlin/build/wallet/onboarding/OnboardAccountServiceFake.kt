package build.wallet.onboarding

import app.cash.turbine.test
import build.wallet.coroutines.turbine.awaitUntil
import build.wallet.testing.shouldBeOk
import com.github.michaelbull.result.Err
import com.github.michaelbull.result.Ok
import com.github.michaelbull.result.Result
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update

class OnboardAccountServiceFake : OnboardAccountService {
  private val pendingSteps = MutableStateFlow(listOf<OnboardAccountStep?>(null))

  fun setPendingSteps(vararg steps: OnboardAccountStep) {
    pendingSteps.update {
      steps.toList()
    }
  }

  override suspend fun pendingStep(): Result<OnboardAccountStep?, Throwable> {
    return Ok(pendingSteps.value.firstOrNull())
  }

  var completeStepError: Throwable? = null

  override suspend fun completeStep(step: OnboardAccountStep): Result<Unit, Throwable> {
    if (completeStepError != null) {
      return Err(completeStepError!!)
    }
    pendingSteps.update {
      val updated = it.toMutableList()
      updated.remove(step)
      updated
    }
    return Ok(Unit)
  }

  suspend fun awaitPendingStep(step: OnboardAccountStep?): OnboardAccountStep? {
    pendingSteps.test { awaitUntil { it.firstOrNull() == step } }
    return pendingStep().shouldBeOk().shouldBe(step)
  }

  fun reset() {
    completeStepError = null
    pendingSteps.value = listOf(null)
  }
}
