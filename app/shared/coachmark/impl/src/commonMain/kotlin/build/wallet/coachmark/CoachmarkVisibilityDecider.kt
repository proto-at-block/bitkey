package build.wallet.coachmark

import build.wallet.feature.flags.InAppSecurityFeatureFlag
import build.wallet.feature.isEnabled
import kotlinx.datetime.Clock

/**
 * Maps coachmark identifiers to feature flags. This allows them to be turned on
 * and off with the feature flags.
 *
 * @param inAppSecurityFeatureFlag The in-app security feature flag.
 */
class CoachmarkVisibilityDecider(
  val inAppSecurityFeatureFlag: InAppSecurityFeatureFlag,
  val clock: Clock,
) {
  fun shouldShow(coachmark: Coachmark): Boolean {
    val featureFlagged = when (coachmark.id) {
      CoachmarkIdentifier.HiddenBalanceCoachmark -> inAppSecurityFeatureFlag.isEnabled()
      CoachmarkIdentifier.BiometricUnlockCoachmark -> inAppSecurityFeatureFlag.isEnabled()
      else -> {
        // Not all coachmarks have associated feature flags
        true
      }
    }
    return coachmark.expiration > clock.now() && !coachmark.viewed && featureFlagged
  }
}
