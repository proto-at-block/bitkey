package build.wallet

import com.github.michaelbull.result.Ok
import com.github.michaelbull.result.Result
import com.github.michaelbull.result.coroutines.runSuspendCatching
import com.github.michaelbull.result.map
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * An alias to [Result.runSuspendCatching]. This is the preferred way to safely execute throwing
 * code when using [Result]. Unlike [Result.runCatching] and Kotlin's [runCatching], this function
 * will re-throw [CancellationException] to indicate normal cancellation of a coroutine, which is
 * what we want for coroutines to be able to properly signal cancellations.
 *
 * See [Coroutines cancellation and timeouts](https://kotlinlang.org/docs/cancellation-and-timeouts.html#making-computation-code-cancellable).
 */
inline fun <V> Result.Companion.catching(block: () -> V): Result<V, Throwable> =
  runSuspendCatching(block)

/**
 * A slightly more readable way to map successful results to [Unit]. Useful when consumer doesn't
 * care about [V] value anymore and needs to use `Ok(Unit)` as a signal for successful result.
 */
inline fun <V, E> Result<V, E>.mapUnit(): Result<Unit, E> = map { }

/**
 * Calls [Result.map] with [transform] on each item emitted by this Flow.
 * A shortcut for `flow.map { result -> result.map(transform) } }`.
 */
inline fun <V, E, U> Flow<Result<V, E>>.mapResult(
  crossinline transform: suspend (V) -> U,
): Flow<Result<U, E>> = map { result -> result.map { transform(it) } }

fun <V, E> Result<V, E>.isOk(): Boolean = this is Ok
