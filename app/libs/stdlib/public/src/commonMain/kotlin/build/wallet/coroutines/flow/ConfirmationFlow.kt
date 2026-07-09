package build.wallet.coroutines.flow

import build.wallet.catchingResult
import com.github.michaelbull.result.onFailure
import com.github.michaelbull.result.onSuccess
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.isActive
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

typealias ConfirmationFlow<T> = Flow<ConfirmationState<T>>

/**
 * Possible states/results of a generic confirmation operation.
 *
 * This is used to control the user flow when the user is asked to confirm or
 * verify something while the app waits.
 */
sealed interface ConfirmationState<out T> {
  /**
   * App is still waiting for the user to finish confirmation.
   */
  data object Pending : ConfirmationState<Nothing>

  /**
   * Any Terminal state of the confirmation operation.
   */
  sealed interface Complete<out T> : ConfirmationState<T>

  /**
   * User has rejected the confirmation request externally.
   */
  data object Rejected : Complete<Nothing>

  /**
   * Confirmation operation timed out before it could be completed by the user.
   */
  data object Expired : Complete<Nothing>

  /**
   * User has successfully confirmed the operation.
   */
  data class Confirmed<T>(val data: T) : Complete<T>
}

/**
 * Create a flow that repeats an operation to check the status of a
 * confirmation operation.
 *
 * This will immediately run the operation once, and repeat until the
 * confirmation reaches a terminal state.
 *
 * If an unexpected error is thrown while running the operation, it will be
 * swallowed and the operation will continue polling. Callers should perform
 * their own logging inside [operation] for failures they want to observe.
 *
 * @param delay Amount of time to wait after each run of the operation.
 * @param onCancel Invoked when the flow is canceled, before the cancellation
 *   propagates. Use this to release any external resources tied to the
 *   confirmation (e.g. cancelling a server-side request).
 * @param operation Invoked repeatedly to get a new state for the confirmation.
 */
// SuspendFunSwallowedCancellation: CancellationException is rethrown immediately after the
// documented onCancel cleanup hook runs — cancellation is never swallowed.
@Suppress("SuspendFunSwallowedCancellation")
fun <T> pollForConfirmation(
  delay: Duration = 5.seconds,
  onCancel: suspend () -> Unit = {},
  operation: suspend () -> ConfirmationState<T>,
): ConfirmationFlow<T> {
  return flow {
    try {
      while (currentCoroutineContext().isActive) {
        catchingResult {
          operation().also { emit(it) }
        }.onFailure {
          // Swallow unexpected errors and continue polling.
        }.onSuccess {
          if (it is ConfirmationState.Complete) return@flow
        }

        delay(delay)
      }
    } catch (e: CancellationException) {
      onCancel()
      throw e
    }
  }
}
