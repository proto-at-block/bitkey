package build.wallet.statemachine.data.account.create

import build.wallet.onboarding.OnboardingKeyboxStep
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update

class OnboardingStepSkipConfigDaoFake : OnboardingStepSkipConfigDao {
  private val stepsToSkip = MutableStateFlow(emptySet<OnboardingKeyboxStep>())

  override fun stepsToSkip(): Flow<Set<OnboardingKeyboxStep>> {
    return stepsToSkip
  }

  override suspend fun setShouldSkipOnboardingStep(
    step: OnboardingKeyboxStep,
    shouldSkip: Boolean,
  ) {
    stepsToSkip.update {
      when {
        shouldSkip -> it + step
        else -> it - step
      }
    }
  }

  fun reset() {
    stepsToSkip.value = emptySet()
  }
}
