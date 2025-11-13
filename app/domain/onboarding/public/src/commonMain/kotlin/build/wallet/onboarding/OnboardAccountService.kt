package build.wallet.onboarding

import com.github.michaelbull.result.Result

interface OnboardAccountService {
  /**
   * Returns the currently pending onboarding step that customer needs to complete to
   * make progress for onboarding their account.
   *
   * If return `null`, indicates that all onboarding steps are complete.
   */
  suspend fun pendingStep(): Result<OnboardAccountStep?, Throwable>

  /**
   * Marks onboarding step as complete.
   */
  suspend fun completeStep(step: OnboardAccountStep): Result<Unit, Throwable>

  /**
   * Marks a step as incomplete. Only the type of [OnboardAccountStep] matters, any fields
   * are ignored.
   */
  suspend fun markStepIncomplete(step: OnboardAccountStep): Result<Unit, Throwable>
}
