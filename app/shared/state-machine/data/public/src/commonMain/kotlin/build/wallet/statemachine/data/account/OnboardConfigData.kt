package build.wallet.statemachine.data.account

import build.wallet.onboarding.OnboardingKeyboxStep

/**
 * Represents state of [OnboardConfig], allows to update the config.
 */
sealed interface OnboardConfigData {
  /**
   * Indicates that [OnboardConfig] is being loaded.
   */
  data object LoadingOnboardConfigData : OnboardConfigData

  /**
   * Indicates that current [OnboardConfig] is loaded.
   *
   * @property config current [OnboardConfig].
   */
  data class LoadedOnboardConfigData(
    val config: OnboardConfig,
    val setShouldSkipStep: (step: OnboardingKeyboxStep, shouldSkip: Boolean) -> Unit,
  ) : OnboardConfigData
}
