package build.wallet.testing

import build.wallet.LoadableValue
import build.wallet.LoadableValue.LoadedValue
import com.github.michaelbull.result.*
import io.kotest.assertions.asClue
import io.kotest.assertions.fail
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.should
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.kotest.matchers.types.shouldBeTypeOf

/**
 * Verifies that result is [Err] with exact [error] of type [E].
 */
inline fun <reified E : Throwable> Result<Any?, Any>.shouldBeErr(error: E) {
  asClue {
    shouldBeErrOfType<E>().should {
      it.message.shouldBe(error.message)
      it.cause.shouldBe(error.cause)
    }
  }
}

/**
 * Verifies that result is [Err] and [Err.error] is an instance of [E].
 *
 * Necessary because [shouldBeInstanceOf] handles generics incorrectly.
 * See: https://github.com/kotest/kotest/issues/3524
 */
inline fun <reified E : Any> Result<Any?, Any?>.shouldBeErrOfType(): E {
  return getError().shouldBeInstanceOf<E>()
}

/**
 * Verifies that result is [Ok] and [Ok.value] is an instance of [V].
 *
 * Necessary because [shouldBeInstanceOf] handles generics incorrectly.
 * See: https://github.com/kotest/kotest/issues/3524
 */
inline fun <reified V : Any> Result<Any?, Any?>.shouldBeOkOfType(): V {
  return get().shouldBeInstanceOf<V>()
}

/**
 * Verifies that result is [Ok] and [Ok.value] is an instance of [V].
 *
 * Necessary because [shouldBeInstanceOf] handles generics incorrectly.
 * See: https://giulthub.com/kotest/kotest/issues/3524
 */
inline fun <reified V : Any?> Result<V, Any?>.shouldBeOk(noinline matcher: (V) -> Unit = {}): V =
  if (isOk) {
    (get() as V).also { it.should(matcher) }
  } else {
    getError().shouldBeNull()
    fail("")
  }

/**
 * Verifies that result is [Ok] with exact [value].
 */
inline fun <reified V : Any?> Result<V, Any?>.shouldBeOk(value: V): V = get().shouldBe(value) as V

/**
 * Verifies that this [LoadableValue] is a loaded value.
 */
@Suppress("UNCHECKED_CAST")
inline fun <reified V : Any?> LoadableValue<V>.shouldBeLoaded(): V =
  shouldBeTypeOf<LoadedValue<V>>().value

/**
 * Verifies that this [LoadableValue] is a specific loaded value.
 */
inline fun <reified V : Any?> LoadableValue<V>.shouldBeLoaded(value: V): V =
  shouldBeLoaded().shouldBe(value)
