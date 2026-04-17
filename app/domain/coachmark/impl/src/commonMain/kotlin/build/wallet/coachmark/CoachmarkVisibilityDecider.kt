package build.wallet.coachmark

import build.wallet.di.AppScope
import build.wallet.di.BitkeyInject
import build.wallet.feature.flags.PrivateWalletMigrationFeatureFlag
// import build.wallet.feature.isEnabled  // unused while PrivateWalletHomeCoachmark is hard-coded off
import kotlinx.datetime.Clock

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
) {
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
      else -> {
        // Not all coachmarks have associated feature flags
        true
      }
    }
    val notExpired = coachmark.expiration?.let { it > clock.now() } ?: true

    return notExpired && !coachmark.viewed && featureFlagged
  }
}
