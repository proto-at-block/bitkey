package build.wallet

import com.github.michaelbull.result.Err
import com.github.michaelbull.result.ResultBinding
import com.github.michaelbull.result.coroutines.binding.SuspendableResultBinding

/**
 * Binds [Err] with [error] if [predicate] is false.
 *
 * Suspend version.
 */
suspend fun <E> SuspendableResultBinding<E>.ensure(
  predicate: Boolean,
  error: () -> E,
) {
  @Suppress("IMPLICIT_NOTHING_TYPE_ARGUMENT_IN_RETURN_POSITION")
  if (!predicate) Err(error()).bind()
}

/**
 * Binds [Err] with [error] if [predicate] is false.
 *
 * Non suspend version.
 */
fun <E> ResultBinding<E>.ensure(
  predicate: Boolean,
  error: () -> E,
) {
  @Suppress("IMPLICIT_NOTHING_TYPE_ARGUMENT_IN_RETURN_POSITION")
  if (!predicate) Err(error()).bind()
}
