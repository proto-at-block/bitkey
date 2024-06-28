package build.wallet.coachmark

import build.wallet.feature.flags.InAppSecurityFeatureFlag
import build.wallet.feature.flags.MultipleFingerprintsIsEnabledFeatureFlag
import build.wallet.feature.isEnabled

/**
 * Maps coachmark identifiers to feature flags. This allows them to be turned on
 * and off with the feature flags.
 *
 * @param inAppSecurityFeatureFlag The in-app security feature flag.
 * @param multipleFingerprintsFeatureFlag The multiple fingerprints feature flag.
 */
class CoachmarkFeatureFlagVisibilityDecider(
  val inAppSecurityFeatureFlag: InAppSecurityFeatureFlag,
  val multipleFingerprintsFeatureFlag: MultipleFingerprintsIsEnabledFeatureFlag,
) {
  fun shouldShow(coachmarkId: String): Boolean =
    when (coachmarkId) {
      CoachmarkIdentifier.HiddenBalanceCoachmark.string -> inAppSecurityFeatureFlag.isEnabled()
      CoachmarkIdentifier.MultipleFingerprintsCoachmark.string ->
        multipleFingerprintsFeatureFlag
          .isEnabled()
      CoachmarkIdentifier.BiometricUnlockCoachmark.string -> inAppSecurityFeatureFlag.isEnabled()
      else -> {
        // Not all coachmarks have associated feature flags
        true
      }
    }
}
