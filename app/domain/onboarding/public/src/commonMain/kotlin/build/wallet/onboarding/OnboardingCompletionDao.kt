package build.wallet.onboarding

import com.github.michaelbull.result.Result
import kotlinx.datetime.Instant

interface OnboardingCompletionDao {
  val onboardingKeyId: String
    get() = "onboarding_completion"

  /**
   * Records the timestamp when onboarding was completed.
   */
  suspend fun recordCompletion(
    id: String = onboardingKeyId,
    timestamp: Instant,
  ): Result<Unit, Error>

  /**
   * Records the onboarding completion timestamp for users who completed onboarding
   * before this tracking was implemented.
   */
  suspend fun recordCompletionIfNotExists(
    id: String = onboardingKeyId,
    timestamp: Instant,
  ): Result<Unit, Error>

  /**
   * Gets the timestamp when onboarding was completed.
   * Returns null if onboarding has not been completed.
   */
  suspend fun getCompletionTimestamp(id: String = onboardingKeyId): Result<Instant?, Error>

  /**
   * Clears the onboarding completion timestamp.
   */
  suspend fun clearCompletionTimestamp(id: String = onboardingKeyId): Result<Unit, Error>
}
