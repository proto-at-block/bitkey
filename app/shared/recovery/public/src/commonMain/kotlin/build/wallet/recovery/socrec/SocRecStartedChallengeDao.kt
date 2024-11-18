package build.wallet.recovery.socrec

import com.github.michaelbull.result.Result

/**
 * Stores a local reference to a social recovery challenge that is in progress.
 */
interface SocRecStartedChallengeDao {
  /**
   * Get the ID of an in-progress local social challenge, if any.
   */
  suspend fun get(): Result<String?, Error>

  /**
   * Save a social challenge as pending.
   */
  suspend fun set(challengeId: String): Result<Unit, Error>

  /**
   * Remove any pending social challenge.
   */
  suspend fun clear(): Result<Unit, Error>
}
