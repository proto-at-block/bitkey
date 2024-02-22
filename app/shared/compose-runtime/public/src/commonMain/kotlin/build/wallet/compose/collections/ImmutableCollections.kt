package build.wallet.compose.collections

import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.PersistentList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toPersistentList

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
 * Returns an empty persistent list.
 */
fun <E> emptyImmutableList(): ImmutableList<E> =
  @Suppress("DEPRECATION")
  kotlinx.collections.immutable.immutableListOf()

fun <E : Any> persistentListOfNotNull(vararg elements: E?): PersistentList<E> =
  persistentListOf<E>().addAll(elements.filterNotNull())

inline fun <E> buildPersistentList(
  builder: PersistentListBuilderScope<E>.() -> Unit,
): PersistentList<E> {
  return buildList {
    val scope =
      object : PersistentListBuilderScope<E> {
        override fun E.unaryPlus() {
          add(this)
        }

        override fun List<E>.unaryPlus() {
          addAll(this)
        }
      }
    scope.builder()
  }.toPersistentList()
}

interface PersistentListBuilderScope<E> {
  operator fun E.unaryPlus()

  operator fun List<E>.unaryPlus()
}
