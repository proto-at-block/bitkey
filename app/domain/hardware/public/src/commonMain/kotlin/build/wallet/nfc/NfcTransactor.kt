package build.wallet.nfc

import build.wallet.nfc.platform.NfcCommands
import com.github.michaelbull.result.Result
import com.github.michaelbull.result.onFailure
import com.github.michaelbull.result.onSuccess
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.buffer
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.onEach

/**
 * This class is the thin-waist for all application code to make NFC operations via its transact method.
 * It has the platform [NfcSession] and [NfcCommands] injected at creation.
 **/

typealias TransactionFn<T> = suspend (session: NfcSession, commands: NfcCommands) -> T

/**
 * Lifecycle and terminal events emitted by [NfcTransactor.transactEvents].
 *
 * Modeled as a sealed hierarchy so callers can consume the entire NFC transaction as an
 * ordered [Flow] rather than wiring the non-suspending [NfcSession.Parameters] callbacks
 * directly (which are invoked on platform NFC threads — CoreNFC delegate queue on iOS,
 * NfcAdapter dispatch thread on Android).
 *
 * Events arrive in source order with no drops; conflation, if desired, belongs at the
 * UI render boundary via `.conflate()` downstream of any per-event side effects.
 */
sealed interface NfcTransactionEvent<out T> {
  data class TagConnected(val session: NfcSession?) : NfcTransactionEvent<Nothing>

  data object TagDisconnected : NfcTransactionEvent<Nothing>

  data object SessionCanceled : NfcTransactionEvent<Nothing>

  data class Succeeded<T>(val result: T) : NfcTransactionEvent<T>

  data class Failed(val error: NfcException) : NfcTransactionEvent<Nothing>
}

interface NfcTransactor {
  /**
   * Represents the current state of the transactor. True when a transaction is in progress.
   */
  val isTransacting: Boolean

  suspend fun <T> transact(
    parameters: NfcSession.Parameters,
    transaction: TransactionFn<T>,
  ): Result<T, NfcException>

  /**
   * Runs an NFC transaction and emits lifecycle + terminal events with conflation
   * applied at the UI render boundary.
   *
   * Two delivery contracts, by design:
   *
   *  - **[onEvent]** runs for **every** event in source order. Use it for side
   *    effects that must not be dropped: analytics (e.g. `NFC_DETECTED`), mutations
   *    on the [NfcSession] passed via [NfcTransactionEvent.TagConnected.session]
   *    (e.g. `session.message = "…"`), and any handoff to the terminal `Succeeded` /
   *    `Failed` events.
   *
   *  - The **returned [Flow]** is conflated downstream of [onEvent], so collectors
   *    that mutate UI state only see the latest pending event. A rapid
   *    Connected->Disconnected->Connected burst won't make the UI step through every
   *    intermediate frame when the collector is slow (e.g. during recomposition or
   *    inside a success-screen `delay`). The terminal event is always the last item
   *    emitted by the source, so it can never be conflated away.
   *
   * The source is backed by a [Channel.UNLIMITED] buffer so [onEvent] sees every
   * event regardless of how slow the downstream collector is. Any user-supplied
   * callbacks on [parameters] are preserved and still invoked.
   */
  fun <T> transactEvents(
    parameters: NfcSession.Parameters,
    transaction: TransactionFn<T>,
    onEvent: suspend (NfcTransactionEvent<T>) -> Unit = {},
  ): Flow<NfcTransactionEvent<T>> =
    channelFlow {
      parameters.onTagConnectedObservers.add { session ->
        trySend(NfcTransactionEvent.TagConnected(session))
      }
      parameters.onTagDisconnectedObservers.add {
        trySend(NfcTransactionEvent.TagDisconnected)
      }
      parameters.onSessionCanceledObservers.add {
        trySend(NfcTransactionEvent.SessionCanceled)
      }

      transact(parameters = parameters, transaction = transaction)
        .onSuccess { result -> trySend(NfcTransactionEvent.Succeeded(result)) }
        .onFailure { error -> trySend(NfcTransactionEvent.Failed(error)) }
    }
      .buffer(Channel.UNLIMITED)
      .onEach(onEvent)
      .conflate()
}
