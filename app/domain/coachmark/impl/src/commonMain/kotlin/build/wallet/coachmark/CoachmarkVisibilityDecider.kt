package build.wallet.coachmark

import build.wallet.di.AppScope
import build.wallet.di.BitkeyInject
import kotlinx.datetime.Clock

/**
 * Maps coachmark identifiers to feature flags. This allows them to be turned on
 * and off with the feature flags.
 */
@BitkeyInject(AppScope::class)
class CoachmarkVisibilityDecider(
  val clock: Clock,
) {
  fun shouldShow(coachmark: Coachmark): Boolean {
    val featureFlagged = when (coachmark.id) {
      else -> {
        // Not all coachmarks have associated feature flags
        true
      }
    }
    return coachmark.expiration > clock.now() && !coachmark.viewed && featureFlagged
  }
}
