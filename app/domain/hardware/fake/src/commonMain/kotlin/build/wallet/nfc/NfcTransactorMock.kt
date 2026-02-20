package build.wallet.nfc

import app.cash.turbine.Turbine
import com.github.michaelbull.result.Err
import com.github.michaelbull.result.Result
import com.github.michaelbull.result.map
import com.github.michaelbull.result.toErrorIfNull

class NfcTransactorMock(
  turbine: (String) -> Turbine<Any>,
) : NfcTransactor {
  val transactCalls = turbine("transact calls")
  var transactResult: Result<Any, NfcException> = Err(NfcException.UnknownError())

  /**
   * Queue of results to return for consecutive [transact] calls.
   * When non-empty, each call consumes and returns the first result from the queue.
   * When empty, falls back to [transactResult].
   */
  private val transactResultQueue = mutableListOf<Result<Any, NfcException>>()

  override var isTransacting: Boolean = false

  /**
   * Queues multiple results to be returned by consecutive [transact] calls.
   * Results are consumed in order; after the queue is exhausted, [transactResult] is used.
   */
  fun queueTransactResults(results: List<Result<Any, NfcException>>) {
    transactResultQueue.addAll(results)
  }

  override suspend fun <T> transact(
    parameters: NfcSession.Parameters,
    transaction: TransactionFn<T>,
  ): Result<T, NfcException> {
    isTransacting = true
    transactCalls.add(parameters)
    val result = if (transactResultQueue.isNotEmpty()) {
      transactResultQueue.removeAt(0)
    } else {
      transactResult
    }
    return result.map { it as? T }
      .toErrorIfNull { NfcException.UnknownError() }
      .also {
        isTransacting = false
      }
  }

  fun reset() {
    transactResult = Err(NfcException.UnknownError())
    transactResultQueue.clear()
  }
}
