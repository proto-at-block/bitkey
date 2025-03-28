package build.wallet.bitcoin.transactions

import com.github.michaelbull.result.Result

/**
 * Stores the users most recently used Transaction Priority selection
 */
interface TransactionPriorityPreference {
  /**
   * Get the stored priority. When there is none, null is returned
   */
  suspend fun get(): EstimatedTransactionPriority?

  /**
   * Set the priority preference of the user
   *
   * @param priority - the [EstimatedTransactionPriority] to be stored
   */
  suspend fun set(priority: EstimatedTransactionPriority)

  /**
   * Clears the persisted preference
   */
  suspend fun clear(): Result<Unit, Error>
}
