package build.wallet.nfc

import app.cash.turbine.Turbine
import com.github.michaelbull.result.Err
import com.github.michaelbull.result.Result
import com.github.michaelbull.result.map
import com.github.michaelbull.result.toErrorIfNull
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.cancel

class NfcTransactorMock(
  turbine: (String) -> Turbine<Any>,
) : NfcTransactor {
  val transactCalls = turbine("transact calls")
  var transactResult: Result<Any, NfcException> = Err(NfcException.UnknownError())

  /**
   * Optional hook invoked synchronously at the start of [transact] (after the call is
   * recorded but before any pause gate or result resolution). Tests use this to drive
   * platform NFC callbacks — e.g. `parameters.onTagConnected(null)` — so that
   * lifecycle events can be observed via [NfcTransactor.transactEvents].
   */
  var onTransactStarted: ((NfcSession.Parameters) -> Unit)? = null

  /**
   * Queue of results to return for consecutive [transact] calls.
   * When non-empty, each call consumes and returns the first result from the queue.
   * When empty, falls back to [transactResult].
   */
  private val transactResultQueue = mutableListOf<Result<Any, NfcException>>()
  private val transactPauseGates = mutableListOf<CompletableDeferred<Unit>>()

  override var isTransacting: Boolean = false

  /**
   * Queues multiple results to be returned by consecutive [transact] calls.
   * Results are consumed in order; after the queue is exhausted, [transactResult] is used.
   */
  fun queueTransactResults(results: List<Result<Any, NfcException>>) {
    transactResultQueue.addAll(results)
  }

  /**
   * Pauses the next [transact] call until the returned gate is completed or cancelled.
   */
  fun pauseNextTransact(): CompletableDeferred<Unit> {
    return CompletableDeferred<Unit>().also { gate ->
      transactPauseGates.add(gate)
    }
  }

  override suspend fun <T> transact(
    parameters: NfcSession.Parameters,
    transaction: TransactionFn<T>,
  ): Result<T, NfcException> {
    isTransacting = true
    transactCalls.add(parameters)
    return try {
      onTransactStarted?.invoke(parameters)

      transactPauseGates.firstOrNull()?.let { gate ->
        transactPauseGates.removeAt(0)
        gate.await()
      }

      val result = if (transactResultQueue.isNotEmpty()) {
        transactResultQueue.removeAt(0)
      } else {
        transactResult
      }
      result.map { it as? T }
        .toErrorIfNull { NfcException.UnknownError() }
    } finally {
      isTransacting = false
    }
  }

  fun reset() {
    transactResult = Err(NfcException.UnknownError())
    onTransactStarted = null
    transactResultQueue.clear()
    transactPauseGates.forEach { it.cancel() }
    transactPauseGates.clear()
  }
}
