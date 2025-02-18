package build.wallet.coroutines

import build.wallet.catchingResult
import com.github.michaelbull.result.Result
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeout
import kotlin.time.Duration

/**
 * Just like [withTimeout] but instead of throwing [TimeoutCancellationException] (which implements [CancellationException]),
 * throws non-cancellation [TimeoutException]. The issue with [TimeoutCancellationException] is that
 * instead of bubbling up timeout exception to parent coroutines, the exception is swallowed as
 * a [CancellationException]. We want the timeout exception to be thrown instead.
 *
 * For this reason, [withTimeoutResult] and [withTimeoutThrowing] should be always preferred over kotlin's [withTimeout].
 *
 * See https://github.com/Kotlin/kotlinx.coroutines/issues/1374.
 */
suspend fun <T> withTimeoutThrowing(
  timeout: Duration,
  block: suspend () -> T,
): T =
  try {
    @Suppress("ForbiddenMethodCall")
    withTimeout(timeout) { block() }
  } catch (e: TimeoutCancellationException) {
    throw TimeoutException(e)
  }

/**
 * Thrown by [withTimeoutThrowing] when the timeout is reached to execute a coroutine.
 */
class TimeoutException(cause: Exception) : Exception(cause)

/**
 * Same as [withTimeoutThrowing] but returns [Result] instead of throwable.
 */
suspend fun <T> withTimeoutResult(
  timeout: Duration,
  block: suspend () -> T,
): Result<T, Throwable> = catchingResult { withTimeoutThrowing(timeout, block) }
