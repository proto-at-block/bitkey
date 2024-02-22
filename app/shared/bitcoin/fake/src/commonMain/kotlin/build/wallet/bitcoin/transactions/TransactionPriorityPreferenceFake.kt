package build.wallet.bitcoin.transactions

import com.github.michaelbull.result.Ok
import com.github.michaelbull.result.Result

/**
 * Fake [TransactionPriorityPreference] baked by in memory storage.
 */
class TransactionPriorityPreferenceFake : TransactionPriorityPreference {
  var preference: EstimatedTransactionPriority? = null

  override suspend fun get(): EstimatedTransactionPriority? {
    return preference
  }

  override suspend fun set(priority: EstimatedTransactionPriority) {
    preference = priority
  }

  override suspend fun clear(): Result<Unit, Error> {
    reset()
    return Ok(Unit)
  }

  fun reset() {
    preference = null
  }
}
