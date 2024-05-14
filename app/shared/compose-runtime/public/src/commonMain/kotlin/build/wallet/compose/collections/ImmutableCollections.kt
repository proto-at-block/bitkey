package build.wallet.compose.collections

import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList
import kotlin.experimental.ExperimentalTypeInference

/**
 * Creates [ImmutableList].
 *
 * There is already [kotlinx.collections.immutable.immutableListOf] extension but strangely it is
 * marked as deprecated: https://github.com/Kotlin/kotlinx.collections.immutable/issues/62.
 *
 * This is our own shortcut to create [ImmutableList] without deprecated call.
 */
fun <E> immutableListOf(vararg elements: E): ImmutableList<E> =
  @Suppress("DEPRECATION")
  kotlinx.collections.immutable.immutableListOf(*elements)

/**
 * Like [listOfNotNull] but returns an [ImmutableList].
 */
inline fun <reified E> immutableListOfNotNull(vararg elements: E?): ImmutableList<E> =
  buildImmutableList {
    elements.forEach { element ->
      element?.let(::add)
    }
  }

/**
 * Returns an empty persistent list.
 */
fun <E> emptyImmutableList(): ImmutableList<E> =
  @Suppress("DEPRECATION")
  kotlinx.collections.immutable.immutableListOf()

/**
 * Like [buildList] but returns an [ImmutableList].
 */
@OptIn(ExperimentalTypeInference::class)
inline fun <E> buildImmutableList(
  @BuilderInference builderAction: MutableList<E>.() -> Unit,
): ImmutableList<E> {
  return buildList(builderAction).toImmutableList()
}
