package build.wallet.coachmark

import com.github.michaelbull.result.Result
import kotlinx.datetime.Instant

/**
 * Coachmark data access object.
 */
interface CoachmarkDao {
  /**
   * Add a coachmark to the DB.
   *
   * @param id The coachmark identifier.
   * @param expiration The expiration date of the coachmark. (2 weeks from creation by default)
   * @return A result indicating success or failure.
   */
  suspend fun insertCoachmark(
    id: CoachmarkIdentifier,
    expiration: Instant,
  ): Result<Unit, Error>

  /**
   * Set a coachmark as viewed.
   *
   * @param id The coachmark identifier.
   * @return The coachmark if it was updated successfully, or an error if it wasn't.
   */
  suspend fun setViewed(id: CoachmarkIdentifier): Result<Unit, Error>

  /**
   * Get a coachmark from the DB.
   *
   * @param id The coachmark identifier.
   * @return The coachmark if it exists, or null if it doesn't.
   */
  suspend fun getCoachmark(id: CoachmarkIdentifier): Result<Coachmark?, Error>

  /**
   * Get all coachmarks from the DB.
   * @return A list of all coachmarks in the DB.
   */
  suspend fun getAllCoachmarks(): Result<List<Coachmark>, Error>

  /**
   * Delete all coachmarks in the DB. This is only used for testing purposes.
   * @return A result indicating success or failure.
   */
  suspend fun resetCoachmarks(): Result<Unit, Error>
}
