package build.wallet.statemachine.data.account

import build.wallet.onboarding.OnboardingKeyboxStep

/**
 * Configuration for onboarding a new keybox.
 * Used in development and testing to make the account creation process more seamless.
 */
data class OnboardConfig(
  val stepsToSkip: Set<OnboardingKeyboxStep>,
)
