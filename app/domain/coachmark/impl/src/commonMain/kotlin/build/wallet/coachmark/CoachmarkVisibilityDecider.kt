package build.wallet.coachmark

import build.wallet.di.AppScope
import build.wallet.di.BitkeyInject
import build.wallet.feature.flags.PrivateWalletMigrationFeatureFlag
import build.wallet.feature.flags.W3UpgradeBlockerFeatureFlag
import build.wallet.feature.isEnabled
import build.wallet.onboarding.OnboardingCompletionService
import com.github.michaelbull.result.getOr
import kotlinx.datetime.Clock
import kotlin.time.Duration.Companion.days

/**
 * Maps coachmark identifiers to feature flags. This allows them to be turned on
 * and off with the feature flags.
 *
 * Note: [CoachmarkIdentifier.PrivateWalletHomeCoachmark] is currently hard-coded off as a
 * temporary kill-switch to prevent private wallet migration failures. It bypasses the normal
 * feature-flag path until the underlying issue is resolved.
 */
@BitkeyInject(AppScope::class)
class CoachmarkVisibilityDecider(
  val clock: Clock,
  private val bip177CoachmarkPolicy: Bip177CoachmarkPolicy,
  @Suppress("unused") // retained for DI wiring; will be used again when PrivateWalletHomeCoachmark is re-enabled
  private val privateWalletMigrationFeatureFlag: PrivateWalletMigrationFeatureFlag,
  private val w3UpgradeBlockerFeatureFlag: W3UpgradeBlockerFeatureFlag,
  private val onboardingCompletionService: OnboardingCompletionService,
) {
  companion object {
    /**
     * Minimum time since onboarding completion before showing the W3 upgrade blocker.
     * Matches the inheritance upsell delay so users aren't overwhelmed right after setup.
     */
    internal val W3_UPGRADE_BLOCKER_ONBOARDING_DELAY = 14.days
  }

  /**
   * Returns whether a coachmark is eligible to be created based on feature flags.
   * Used before inserting so we don't start expiration timers prematurely.
   */
  suspend fun shouldCreate(coachmarkId: CoachmarkIdentifier): Boolean =
    when (coachmarkId) {
      CoachmarkIdentifier.Bip177Coachmark -> bip177CoachmarkPolicy.shouldCreate()
      // Hard-coded off: showing the coachmark after cloud recovery causes private wallet
      // migration to fail if the user signs out other devices. Will re-enable after fix.
      CoachmarkIdentifier.PrivateWalletHomeCoachmark -> false
      CoachmarkIdentifier.W3UpgradeBlockerCoachmark ->
        w3UpgradeBlockerFeatureFlag.isEnabled() && hasEnoughTimeSinceOnboarding()
      else -> true
    }

  /**
   * Determines if an existing coachmark should be visible: must be eligible by flags,
   * not viewed, and not expired relative to the current clock.
   * A null expiration means the coachmark never expires.
   */
  suspend fun shouldShow(coachmark: Coachmark): Boolean {
    val featureFlagged = when (coachmark.id) {
      CoachmarkIdentifier.Bip177Coachmark -> bip177CoachmarkPolicy.shouldShow()
      // Hard-coded off: see shouldCreate comment above.
      CoachmarkIdentifier.PrivateWalletHomeCoachmark -> false
      CoachmarkIdentifier.W3UpgradeBlockerCoachmark ->
        w3UpgradeBlockerFeatureFlag.isEnabled() && hasEnoughTimeSinceOnboarding()
      else -> {
        // Not all coachmarks have associated feature flags
        true
      }
    }
    val notExpired = coachmark.expiration?.let { it > clock.now() } ?: true

    return notExpired && !coachmark.viewed && featureFlagged
  }

  /**
   * Returns true if enough time has passed since onboarding. Users without a recorded
   * timestamp (pre-feature installs, freshly recovered devices) are not shown the coachmark
   * yet; Money Home records a timestamp on first render, starting their delay window. This
   * keeps eligibility deterministic instead of racing that first-render write.
   */
  private suspend fun hasEnoughTimeSinceOnboarding(): Boolean {
    val completionTimestamp = onboardingCompletionService.getCompletionTimestamp().getOr(null)
      ?: return false
    return clock.now() - completionTimestamp >= W3_UPGRADE_BLOCKER_ONBOARDING_DELAY
  }
}
