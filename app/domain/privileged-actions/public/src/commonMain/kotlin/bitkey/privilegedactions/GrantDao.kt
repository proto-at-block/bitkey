package bitkey.privilegedactions

import build.wallet.db.DbError
import build.wallet.grants.Grant
import build.wallet.grants.GrantAction
import com.github.michaelbull.result.Result
import kotlinx.coroutines.flow.Flow

/**
 * Data access object for persisting and retrieving [Grant] instances.
 *
 * Supports one grant per action type: save/overwrite grant when fetched from server,
 * retrieve when needed, and delete after successful use.
 */
interface GrantDao {
  /**
   * Saves a grant to the database using the action type as the primary key.
   * If a grant already exists for this action type, it will be overwritten.
   *
   * @param grant The grant data to persist
   */
  suspend fun saveGrant(grant: Grant): Result<Unit, DbError>

  /**
   * Retrieves a grant by action type.
   *
   * @param action The type of grant action
   * @return The grant if found, null otherwise
   */
  suspend fun getGrantByAction(action: GrantAction): Result<Grant?, DbError>

  /**
   * Deletes a grant by action type.
   * Called after the grant has been successfully used.
   *
   * @param action The type of grant action to delete
   */
  suspend fun deleteGrantByAction(action: GrantAction): Result<Unit, DbError>

  /**
   * Returns a flow that emits the current grant for a given action type.
   * The flow will emit whenever the grant is updated or deleted.
   *
   * @param action The type of grant action
   * @return A flow that emits the current grant or null if not found
   */
  fun grantByAction(action: GrantAction): Flow<Grant?>
}
