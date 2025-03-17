package build.wallet.onboarding

import com.github.michaelbull.result.Result
import kotlinx.coroutines.flow.Flow

/**
 * Dao for the state of various steps when onboarding a keybox.
 */
interface OnboardingKeyboxStepStateDao {
  /**
   * Sets the state for the given step
   */
  suspend fun setStateForStep(
    step: OnboardingKeyboxStep,
    state: OnboardingKeyboxStepState,
  ): Result<Unit, Error>

  /**
   * Returns the state for the step as a flow.
   * If no state is stored, returns [Incomplete].
   */
  fun stateForStep(step: OnboardingKeyboxStep): Flow<OnboardingKeyboxStepState>

  /**
   * Clears all of the stored state
   */
  suspend fun clear(): Result<Unit, Error>
}
