package build.wallet.statemachine.data.account.create

import build.wallet.onboarding.OnboardingKeyboxStep
import kotlinx.coroutines.flow.Flow

interface OnboardingStepSkipConfigDao {
  /**
   * Returns a flow of onboarding steps currently configured to be skipped.
   * Used for debugging purposes only.
   */
  fun stepsToSkip(): Flow<Set<OnboardingKeyboxStep>>

  /**
   * Configures the given step to be skipped or not skipped.
   */
  suspend fun setShouldSkipOnboardingStep(
    step: OnboardingKeyboxStep,
    shouldSkip: Boolean,
  )
}
