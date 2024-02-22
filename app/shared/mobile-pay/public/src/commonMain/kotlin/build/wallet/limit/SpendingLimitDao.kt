package build.wallet.limit

import build.wallet.db.DbError
import com.github.michaelbull.result.Result
import kotlinx.coroutines.flow.Flow

interface SpendingLimitDao {
  /**
   * Returns a flow of the local active [SpendingLimit].
   */
  fun activeSpendingLimit(): Flow<SpendingLimit?>

  /**
   * Retrieves the local active [SpendingLimit] if one exists.
   */
  suspend fun getActiveSpendingLimit(): Result<SpendingLimit?, DbError>

  /**
   * Retrieves the most recent local [SpendingLimit] if one exists.
   *
   * Will return the current active spending limit if one is active, otherwise,
   * will return the last active spending limit if one exists, otherwise returns null.
   */
  suspend fun mostRecentSpendingLimit(): Result<SpendingLimit?, DbError>

  /**
   * Locally persists the given [SpendingLimit] and signatures and sets
   * the limit as active.
   */
  suspend fun saveAndSetSpendingLimit(limit: SpendingLimit): Result<Unit, DbError>

  /**
   * Disables the spending limit.
   */
  suspend fun disableSpendingLimit(): Result<Unit, DbError>

  /**
   * Removes all limits
   */
  suspend fun removeAllLimits(): Result<Unit, DbError>
}
