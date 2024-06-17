package build.wallet

import com.github.michaelbull.result.BindingScope
import com.github.michaelbull.result.Err
import com.github.michaelbull.result.coroutines.CoroutineBindingScope

/**
 * Binds [Err] with [error] if [predicate] is false.
 *
 * Suspend version.
 */
suspend fun <E> CoroutineBindingScope<E>.ensure(
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
fun <E> BindingScope<E>.ensure(
  predicate: Boolean,
  error: () -> E,
) {
  @Suppress("IMPLICIT_NOTHING_TYPE_ARGUMENT_IN_RETURN_POSITION")
  if (!predicate) Err(error()).bind()
}
