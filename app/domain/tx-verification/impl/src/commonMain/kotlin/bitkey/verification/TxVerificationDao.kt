package bitkey.verification

import build.wallet.db.DbError
import com.github.michaelbull.result.Result
import kotlinx.coroutines.flow.Flow

/**
 * Provides the locally stored information on transaction verification states.
 */
interface TxVerificationDao {
  /**
   * Create a local active policy record for the specified threshold.
   *
   * Note: This will drop any current policy data that is active, and should
   * only be used when the server's policy is known to be active.
   *
   * @param threshold The spending threshold the transaction policy applies to.
   */
  suspend fun setActivePolicy(
    threshold: VerificationThreshold,
  ): Result<TxVerificationPolicy.Active, Error>

  /**
   * Create a local policy record from the specified limits.
   *
   * This inserts a pending policy record into the local cache for later
   * completion. Once the authorization is completed, the policy can be
   * made active by calling [promotePolicy].
   *
   * @param threshold The spending threshold the transaction policy will apply to when activated.
   * @param auth information required for for completing the policy auth with the server.
   */
  suspend fun createPendingPolicy(
    threshold: VerificationThreshold,
    auth: TxVerificationPolicy.DelayNotifyAuthorization,
  ): Result<TxVerificationPolicy.Pending, Error>

  /**
   * Make the specified policy into an active policy.
   *
   * Note: This will drop any current policy data that is active, and should
   * only be used when the server's policy is known to be active.
   *
   * This should be invoked once any server authorization steps are completed
   * so that the application moves the policy out of a pending state and
   * uses it as the current verification policy.
   */
  suspend fun promotePolicy(id: TxVerificationPolicy.PolicyId): Result<Unit, DbError>

  /**
   * Emits the latest transaction verification policy that is in effect.
   *
   * If there are no policies set yet, this will return null.
   */
  suspend fun getActivePolicy(): Flow<Result<TxVerificationPolicy.Active?, Error>>

  /**
   * Emits a list of all policies that have never been marked in-effect.
   *
   * Note: If any one of the policies is invalid, it will be removed from
   * the returned list and logged, rather than failing this query. The error
   * return for this query is only if a database / schema error occurs.
   */
  suspend fun getPendingPolicies(): Flow<Result<List<TxVerificationPolicy.Pending>, DbError>>

  /**
   * Remove a policy from the database.
   *
   * Disabling verification should NOT require removing the existing policy.
   * Instead, create a new policy with no amount. This is necessary because
   * removing the policy may require authorization from the server before
   * it is put into effect.
   */
  suspend fun deletePolicy(id: TxVerificationPolicy.PolicyId): Result<Unit, DbError>

  /**
   * Reset all local policy data.
   */
  suspend fun clear(): Result<Unit, DbError>
}
