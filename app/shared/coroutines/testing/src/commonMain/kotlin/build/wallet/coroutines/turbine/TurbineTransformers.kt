package build.wallet.coroutines.turbine

import app.cash.turbine.ReceiveTurbine
import app.cash.turbine.Turbine
import io.kotest.matchers.types.shouldBeTypeOf

/**
 * Requires original [ReceiveTurbine] turbine to emit items of type [R].
 * If emitted items do not match expected type [R], assertion error is raised.
 *
 * Returns a delegate implementation of [ReceiveTurbine] without consuming items emitted by original
 * [ReceiveTurbine] instance.
 */
suspend inline fun <reified R : Any> ReceiveTurbine<out Any>.withTypeOf(): ReceiveTurbine<R> {
  val originalTurbine = this
  return object : ReceiveTurbine<R> by Turbine() {
    override suspend fun awaitItem(): R {
      return originalTurbine.awaitItem().shouldBeTypeOf<R>()
    }

    override fun expectMostRecentItem(): R {
      return originalTurbine.expectMostRecentItem().shouldBeTypeOf<R>()
    }
  }
}

/**
 * Maps items emitted by this [ReceiveTurbine] by applying [transform] block to each item.
 *
 * Returns a delegate implementation of [ReceiveTurbine] without consuming items emitted by original
 * [ReceiveTurbine] instance.
 */
suspend inline fun <reified T : Any, reified R : Any> ReceiveTurbine<out T>.map(
  crossinline transform: (T) -> R,
): ReceiveTurbine<R> {
  val originalTurbine = this

  return object : ReceiveTurbine<R> by Turbine() {
    override suspend fun awaitItem(): R {
      return transform(originalTurbine.awaitItem())
    }

    override fun expectMostRecentItem(): R {
      return transform(originalTurbine.expectMostRecentItem())
    }
  }
}

/**
 * Same as [withTypeOf] but applies [block] to each emitted item and returns its result.
 *
 * @param block function applied to each emitted item of the transformed turbine.
 *
 * @return [Any] value as result of [block].
 */
suspend inline fun <reified R : Any> ReceiveTurbine<out Any>.withTypeOf(
  block: ReceiveTurbine<R>.() -> Any,
): Any = withTypeOf<R>().let(block)

/**
 * Same as [map] but applies [block] to each emitted item and returns its result.
 *
 * @param transform transformation to apply to each emitted item of type [T] to return [R].
 * @param block function applied to each emitted item of the transformed turbine.
 *
 * @return [V] value as result of [block].
 */
suspend inline fun <reified T : Any, reified R : Any, V : Any> ReceiveTurbine<T>.map(
  crossinline transform: (T) -> R,
  block: ReceiveTurbine<R>.() -> V,
): V = map(transform).let(block)
