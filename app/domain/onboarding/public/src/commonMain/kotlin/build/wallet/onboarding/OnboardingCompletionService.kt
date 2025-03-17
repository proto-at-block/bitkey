package build.wallet.onboarding

import com.github.michaelbull.result.Result
import kotlinx.datetime.Instant

interface OnboardingCompletionService {
  /**
   * Records the timestamp when onboarding was completed.
   */
  suspend fun recordCompletion(): Result<Unit, Error>

  /**
   * Records the onboarding completion timestamp for users who completed onboarding
   * before this tracking was implemented.
   */
  suspend fun recordCompletionIfNotExists(): Result<Unit, Error>

  /**
   * Gets the timestamp when onboarding was completed.
   * Returns null if onboarding has not been completed.
   */
  suspend fun getCompletionTimestamp(): Result<Instant?, Error>

  suspend fun clearOnboardingTimestamp(): Result<Unit, Error>
}
