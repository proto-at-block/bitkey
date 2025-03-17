package build.wallet.analytics.events.screen.context

import build.wallet.analytics.events.EventTrackerContext

enum class AuthKeyRotationEventTrackerScreenIdContext : EventTrackerContext {
  /** Auth key rotation was recommended to the user after cloud restoration */
  PROPOSED_ROTATION,

  /** Auth key rotation failed and is being resumed on app startup. */
  FAILED_ATTEMPT,

  /** Auth key rotation initiated by the user from settings. */
  SETTINGS,
}
