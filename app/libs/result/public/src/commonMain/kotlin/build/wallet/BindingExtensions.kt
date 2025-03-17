package build.wallet

import com.github.michaelbull.result.BindingScope
import com.github.michaelbull.result.Err
import com.github.michaelbull.result.coroutines.CoroutineBindingScope
import kotlin.contracts.contract

/**
 * Binds [Err] with [error] if [predicate] is false.
 *
 * Suspend version.
 */
suspend fun <E> CoroutineBindingScope<E>.ensure(
  predicate: Boolean,
  error: () -> E,
) {
  contract { returns() implies predicate }
  @Suppress("IMPLICIT_NOTHING_TYPE_ARGUMENT_IN_RETURN_POSITION")
  if (!predicate) Err(error()).bind()
}

/**
 * Binds [Err] with [error] if [value] is null.
 * Otherwise returns [value].
 *
 * Suspend version.
 */
suspend fun <T, E> CoroutineBindingScope<E>.ensureNotNull(
  value: T?,
  error: () -> E,
): T {
  contract { returns() implies (value != null) }
  ensure(predicate = value != null, error = error)
  return value
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
  contract { returns() implies predicate }
  @Suppress("IMPLICIT_NOTHING_TYPE_ARGUMENT_IN_RETURN_POSITION")
  if (!predicate) Err(error()).bind()
}

/**
 * Binds [Err] with [error] if [value] is null.
 * Otherwise returns [value].
 *
 * Non suspend version.
 */
fun <T, E> BindingScope<E>.ensureNotNull(
  value: T?,
  error: () -> E,
): T {
  contract { returns() implies (value != null) }
  ensure(predicate = value != null, error = error)
  return value
}
