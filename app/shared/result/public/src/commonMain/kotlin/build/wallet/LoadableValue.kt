@file:OptIn(ExperimentalCoroutinesApi::class, ExperimentalContracts::class)

package build.wallet

import build.wallet.LoadableValue.InitialLoading
import build.wallet.LoadableValue.LoadedValue
import com.github.michaelbull.result.Err
import com.github.michaelbull.result.Ok
import com.github.michaelbull.result.Result
import com.github.michaelbull.result.map
import com.github.michaelbull.result.onFailure
import com.github.michaelbull.result.onSuccess
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.transform
import kotlinx.coroutines.flow.transformLatest
import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.contract

/**
 * Helpful in cases where consumer needs a [Flow] to differentiate between [InitialLoading] and
 * [LoadedValue].
 *
 * [Flow.asLoadableValue] is the preferred way to wrap result values in a [Flow] into [LoadableValue].
 * [Flow.unwrapLoadedValue] can be used to unwrap loaded values and ignore [InitialLoading].
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
 * Ignores [InitialLoading] and safely unwraps [LoadedValue.value] on success.
 */
fun <T, E> Flow<Result<LoadableValue<T>, E>>.unwrapLoadedValue(): Flow<Result<T, E>> =
  transform { result ->
    result
      .onSuccess {
        when (it) {
          InitialLoading -> Unit // Ignore initial loading
          is LoadedValue -> emit(Ok(it.value))
        }
      }
      .onFailure { error -> emit(Err(error)) }
  }

/**
 * Wraps successful result values in this [Flow] into [LoadedValue] and emits [InitialLoading] on
 * start. This is the preferred way to convert [Flow] to emit [LoadableValue].
 */
fun <T, E> Flow<Result<T, E>>.asLoadableValue(): Flow<Result<LoadableValue<T>, E>> {
  @Suppress("USELESS_CAST") // Compiler seems to require casting here.
  return map { result -> result.map { LoadedValue(it) as LoadableValue<T> } }
    .onStart { emit(Ok<LoadableValue<T>>(InitialLoading)) }
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
