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
  private val bip177CoachmarkPolicy: Bip177CoachmarkPolicy,
) {
  /**
   * Returns whether a coachmark is eligible to be created based on feature flags.
   * Used before inserting so we don't start expiration timers prematurely.
   */
  suspend fun shouldCreate(coachmarkId: CoachmarkIdentifier): Boolean =
    when (coachmarkId) {
      CoachmarkIdentifier.Bip177Coachmark -> bip177CoachmarkPolicy.shouldCreate()
      else -> true
    }

  /**
   * Determines if an existing coachmark should be visible: must be eligible by flags,
   * not viewed, and not expired relative to the current clock.
   */
  suspend fun shouldShow(coachmark: Coachmark): Boolean {
    val featureFlagged = when (coachmark.id) {
      CoachmarkIdentifier.Bip177Coachmark -> bip177CoachmarkPolicy.shouldShow()
      else -> {
        // Not all coachmarks have associated feature flags
        true
      }
    }
    return coachmark.expiration > clock.now() && !coachmark.viewed && featureFlagged
  }
}
