package bitkey.verification

import build.wallet.db.DbError
import com.github.michaelbull.result.Result
import kotlinx.coroutines.flow.Flow

/**
 * Provides the locally stored information on transaction verification states.
 */
interface TxVerificationDao {
  /**
   * Store the given policy record locally.
   *
   * Note: This does not make the policy effective until `markPolicyEffective` is called.
   */
  suspend fun setPolicy(policy: TxVerificationPolicy): Result<Unit, DbError>

  /**
   * Mark the specified policy as the latest active policy.
   *
   * This should be invoked once any server authorization steps are completed
   * so that the application moves the policy out of a pending state and
   * uses it as the current verification policy.
   */
  suspend fun markPolicyEffective(id: TxVerificationPolicy.Id): Result<Unit, DbError>

  /**
   * Emits the latest transaction verification policy that is in effect.
   *
   * If there are no policies set yet, this will return null.
   */
  suspend fun getEffectivePolicy(): Flow<Result<TxVerificationPolicy?, Error>>

  /**
   * Emits a list of all policies that have never been marked in-effect.
   *
   * Note: If any one of the policies is invalid, it will be removed from
   * the returned list and logged, rather than failing this query. The error
   * return for this query is only if a database / schema error occurs.
   */
  suspend fun getPendingPolicies(): Flow<Result<List<TxVerificationPolicy>, DbError>>

  /**
   * Remove a policy from the database.
   *
   * Disabling verification should NOT require removing the existing policy.
   * Instead, create a new policy with no amount. This is necessary because
   * removing the policy may require authorization from the server before
   * it is put into effect.
   */
  suspend fun deletePolicy(id: TxVerificationPolicy.Id): Result<Unit, DbError>

  /**
   * Reset all local policy data.
   */
  suspend fun clear(): Result<Unit, DbError>
}
