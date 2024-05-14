package build.wallet

import build.wallet.LoadableValue.InitialLoading
import build.wallet.LoadableValue.LoadedValue
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.transformLatest
import kotlin.contracts.contract

/**
 * Helpful in cases where consumer needs a [Flow] to differentiate between [InitialLoading] and
 * [LoadedValue].
 *
 * [Flow.asLoadableValue] is the preferred way to wrap values in a [Flow] into [LoadableValue].
 */
sealed interface LoadableValue<out T> {
  /**
   * Indicates initial loading of the database value while query is being executed for the first
   * time.
   */
  data object InitialLoading : LoadableValue<Nothing>

  /**
   * Indicates that value has successfully loaded.
   */
  data class LoadedValue<out T>(val value: T) : LoadableValue<T>
}

fun <T> LoadableValue<T>.isLoaded(): Boolean {
  contract {
    returns(true) implies (this@isLoaded is LoadedValue<T>)
  }
  return this is LoadedValue
}

/**
 * Applies [transform] to loaded values.
 */
inline infix fun <T, R> LoadableValue<T>.map(transform: (T) -> R): LoadableValue<R> {
  return when (this) {
    InitialLoading -> InitialLoading
    is LoadedValue -> LoadedValue(transform(value))
  }
}

/**
 * Wraps values in this [Flow] into [LoadedValue] and emits [InitialLoading] on
 * start.
 *
 * This is helpful in cases where consumer needs a [Flow] to use explicit type to differentiate
 * between [InitialLoading] and [LoadedValue].
 *
 * This is the preferred way to convert [Flow] to emit [LoadableValue].
 */
fun <T> Flow<T>.asLoadableValue(): Flow<LoadableValue<T>> {
  @Suppress("USELESS_CAST") // Compiler seems to require casting here.
  return map { LoadedValue(it) as LoadableValue<T> }
    .onStart { emit(InitialLoading) }
}

/**
 * Maps [Flow] of [LoadableValue] to [Flow] of [T] by unwrapping [LoadedValue.value].
 *
 * Ignores [InitialLoading] values.
 */
fun <T> Flow<LoadableValue<T>>.mapLoadedValue(): Flow<T> =
  transformLatest {
    if (it is LoadedValue) {
      emit(it.value)
    }
  }
