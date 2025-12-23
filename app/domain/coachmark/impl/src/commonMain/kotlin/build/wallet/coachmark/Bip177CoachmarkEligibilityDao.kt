package build.wallet.coachmark

import com.github.michaelbull.result.Result

/**
 * DAO for tracking BIP 177 coachmark eligibility.
 *
 * This tracks whether the user had sats selected as their Bitcoin display preference
 * when the BIP 177 feature flag was first enabled. This determines if they should see
 * the BIP 177 coachmark (explaining the change from "sats" to the ₿ sign).
 *
 * Users who manually switch to ₿ after BIP 177 is enabled should NOT see the
 * coachmark, since they made that choice consciously.
 */
interface Bip177CoachmarkEligibilityDao {
  /**
   * Returns the stored eligibility status.
   *
   * @return true if user was eligible (had sats when BIP 177 enabled),
   *         false if not eligible,
   *         null if not yet recorded
   */
  suspend fun getEligibility(): Result<Boolean?, Error>

  /**
   * Records the user's eligibility for the BIP 177 coachmark.
   * This should only be called once when the BIP 177 flag is first checked.
   */
  suspend fun setEligibility(eligible: Boolean): Result<Unit, Error>
}
