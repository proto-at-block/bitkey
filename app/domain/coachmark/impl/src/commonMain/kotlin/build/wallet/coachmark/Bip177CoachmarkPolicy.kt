package build.wallet.coachmark

import build.wallet.coachmark.Bip177CoachmarkPolicy.Companion.NEW_USER_THRESHOLD
import build.wallet.di.AppScope
import build.wallet.di.BitkeyInject
import build.wallet.feature.flags.Bip177FeatureFlag
import build.wallet.feature.isEnabled
import build.wallet.money.display.BitcoinDisplayPreferenceRepository
import build.wallet.money.display.BitcoinDisplayUnit
import build.wallet.onboarding.OnboardingCompletionService
import com.github.michaelbull.result.getOr
import kotlinx.datetime.Clock
import kotlin.time.Duration.Companion.minutes

/**
 * BIP 177 coachmark eligibility rules.
 *
 * The coachmark explains the change from "sats" to "₿" symbol. It should only be shown to users
 * who experienced the old "sats" format - users who onboard after BIP 177 is enabled have only
 * ever seen "₿" and don't need the explanation.
 *
 * Users are marked ineligible (and never see the coachmark) if:
 * - They completed onboarding within [NEW_USER_THRESHOLD] of an eligibility check, OR
 * - They had BTC selected when the flag was enabled
 *
 * Eligibility is captured once and persisted.
 */
@BitkeyInject(AppScope::class)
class Bip177CoachmarkPolicy(
  private val clock: Clock,
  private val bip177FeatureFlag: Bip177FeatureFlag,
  private val bitcoinDisplayPreferenceRepository: BitcoinDisplayPreferenceRepository,
  private val bip177CoachmarkEligibilityDao: Bip177CoachmarkEligibilityDao,
  private val onboardingCompletionService: OnboardingCompletionService,
) {
  companion object {
    /**
     * Users who completed onboarding within this duration of an eligibility check are marked
     * ineligible immediately - they onboarded after the flag was enabled and never saw "sats".
     */
    internal val NEW_USER_THRESHOLD = 1.minutes
  }

  /**
   * Determines if the BIP177 coachmark should be created.
   *
   * The coachmark is only created for users who:
   * 1. Already had sats as their Bitcoin display preference when the BIP177 feature
   *    flag was first enabled (not users who manually switch to sats later)
   * 2. Are not "new" users who just completed onboarding (they've never seen "sats")
   */
  suspend fun shouldCreate(): Boolean {
    if (!bip177FeatureFlag.isEnabled()) {
      return false
    }

    // Store false so user stays ineligible even after reopening the app later.
    if (justCompletedOnboarding()) {
      bip177CoachmarkEligibilityDao.setEligibility(false)
      return false
    }

    val storedEligibility = bip177CoachmarkEligibilityDao.getEligibility().getOr(null)

    return when (storedEligibility) {
      null -> {
        // First check after BIP177 enabled - capture current preference as eligibility
        val currentlyHasSats =
          bitcoinDisplayPreferenceRepository.bitcoinDisplayUnit.value == BitcoinDisplayUnit.Satoshi
        bip177CoachmarkEligibilityDao.setEligibility(currentlyHasSats)
        currentlyHasSats
      }
      true -> {
        // User was eligible - still show if on sats
        bitcoinDisplayPreferenceRepository.bitcoinDisplayUnit.value == BitcoinDisplayUnit.Satoshi
      }
      false -> {
        // User ineligible - never show even if they switch to sats later
        false
      }
    }
  }

  /**
   * Determines if the BIP177 coachmark should be shown.
   *
   * Similar to [shouldCreate], but used for existing coachmarks.
   */
  suspend fun shouldShow(): Boolean {
    if (!bip177FeatureFlag.isEnabled()) {
      return false
    }

    val storedEligibility = bip177CoachmarkEligibilityDao.getEligibility().getOr(null)

    // Only show if user was eligible and is still on sats
    return storedEligibility == true &&
      bitcoinDisplayPreferenceRepository.bitcoinDisplayUnit.value == BitcoinDisplayUnit.Satoshi
  }

  private suspend fun justCompletedOnboarding(): Boolean {
    val completionTimestamp = onboardingCompletionService.getCompletionTimestamp().getOr(null)
      ?: return false // No timestamp - user onboarded before tracking and hasn't opened app since

    val timeSinceOnboarding = clock.now() - completionTimestamp
    return timeSinceOnboarding < NEW_USER_THRESHOLD
  }
}
