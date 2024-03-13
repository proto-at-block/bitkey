package build.wallet.analytics.events.screen.context

enum class AuthKeyRotationEventTrackerScreenIdContext : EventTrackerScreenIdContext {
  /** Auth key rotation was recommended to the user after cloud restoration */
  PROPOSED_ROTATION,

  /** Auth key rotation failed and is being resumed on app startup. */
  FAILED_ATTEMPT,

  /** Auth key rotation initiated by the user from settings. */
  SETTINGS,
}
