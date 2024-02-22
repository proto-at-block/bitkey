package build.wallet.recovery.socrec

import build.wallet.db.DbError
import com.github.michaelbull.result.Result

/**
 * Stores a local reference to a social recovery challenge that is in progress.
 */
interface SocRecStartedChallengeDao {
  /**
   * Get the ID of an in-progress local social challenge, if any.
   */
  suspend fun get(): Result<String?, DbError>

  /**
   * Save a social challenge as pending.
   */
  suspend fun set(challengeId: String): Result<Unit, DbError>

  /**
   * Remove any pending social challenge.
   */
  suspend fun clear(): Result<Unit, DbError>
}
