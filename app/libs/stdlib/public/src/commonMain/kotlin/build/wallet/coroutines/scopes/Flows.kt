package build.wallet.coroutines.scopes

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted.Companion.Eagerly
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/**
 * Filter each item in a Flow's emitted collections.
 *
 * This is a convenience method and is equivalent to map/filter.
 */
inline fun <T> Flow<Collection<T>>.filterEach(
  crossinline predicate: (T) -> Boolean,
): Flow<List<T>> {
  return map { it.filter(predicate) }
}

/**
 * Map each item in a StateFlow's emitted values.
 *
 * This is a convenience method and is equivalent to map/stateIn.
 *
 * @param scope - the CoroutineScope to use for the stateIn operator.
 * @param transform - the transformation function to apply to each item.
 */
fun <T, R> StateFlow<T>.mapAsStateFlow(
  scope: CoroutineScope,
  transform: (T) -> R,
): StateFlow<R> {
  return map { transform(it) }.stateIn(scope, Eagerly, transform(this.value))
}
