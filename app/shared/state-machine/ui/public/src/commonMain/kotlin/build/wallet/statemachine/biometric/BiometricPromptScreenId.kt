package build.wallet.statemachine.biometric

import build.wallet.analytics.events.screen.id.EventTrackerScreenId

enum class BiometricPromptScreenId : EventTrackerScreenId {
  /** Screen shown when biometric authentication is in progress */
  BIOMETRIC_PROMPT_SPLASH_SCREEN,
}
