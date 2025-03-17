package build.wallet.coachmark

import com.github.michaelbull.result.Result

/**
 * Coachmark service for identifying which coachmarks to display and marking them as displayed.
 */
interface CoachmarkService {
  /**
   * Provide a set of relevant coachmark identifiers and receive a list of coachmark identifiers that should be displayed.
   */
  suspend fun coachmarksToDisplay(
    coachmarkIds: Set<CoachmarkIdentifier>,
  ): Result<List<CoachmarkIdentifier>, Error>

  /**
   * Called when a coachmark has been displayed (usually on dismiss)
   */
  suspend fun markCoachmarkAsDisplayed(coachmarkId: CoachmarkIdentifier): Result<Unit, Error>

  /**
   * Reset all coachmarks to show again even if they've been viewed (mostly used for testing)
   */
  suspend fun resetCoachmarks(): Result<Unit, Error>
}
