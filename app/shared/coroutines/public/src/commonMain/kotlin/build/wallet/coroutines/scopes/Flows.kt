package build.wallet.coroutines.scopes

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

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
