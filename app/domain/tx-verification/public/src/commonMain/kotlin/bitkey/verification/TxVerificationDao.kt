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
  suspend fun setEnabledThreshold(threshold: VerificationThreshold.Enabled): Result<Unit, Error>

  /**
   * Emits the latest transaction verification policy that is in effect.
   *
   * If there are no policies set yet, this will return null.
   */
  suspend fun getActivePolicy(): Flow<Result<TxVerificationPolicy.Active?, Error>>

  /**
   * Remove a policy from the database.
   *
   * Disabling verification should NOT require removing the existing policy.
   * Instead, create a new policy with no amount. This is necessary because
   * removing the policy may require authorization from the server before
   * it is put into effect.
   */
  suspend fun deletePolicy(): Result<Unit, DbError>
}

/**
 * Sets or removes the transaction verification threshold based based on its type.
 */
suspend fun TxVerificationDao.setThreshold(threshold: VerificationThreshold): Result<Unit, Error> {
  return when (threshold) {
    is VerificationThreshold.Enabled -> setEnabledThreshold(threshold)
    is VerificationThreshold.Disabled -> deletePolicy()
  }
}
