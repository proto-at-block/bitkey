package build.wallet

import com.github.michaelbull.result.Err
import com.github.michaelbull.result.Ok
import com.github.michaelbull.result.binding
import com.github.michaelbull.result.coroutines.coroutineBinding
import io.kotest.assertions.fail
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

class BindingExtensionsTests : FunSpec({

  val someError = Error("test")

  context("suspendBinding") {
    test("ensure - predicate is true, does not bind error") {
      val result = coroutineBinding {
        ensure(true) { someError }
      }

      result.shouldBe(Ok(Unit))
    }

    test("ensure - predicate is false, binds error") {

      val result = coroutineBinding {
        ensure(false) { someError }
      }

      result.shouldBe(Err(someError))
    }

    test("ensureNotNull - value is not null, does not bind error") {
      @Suppress("RedundantNullableReturnType")
      val result = binding {
        val nullableValue: String? = "value"
        val nonNullableValue = ensureNotNull(nullableValue) { someError }
        // Referenced to test type inference
        nonNullableValue.length.shouldBe(5)
        nonNullableValue.shouldBe(nullableValue)
      }

      result.shouldBe(Ok("value"))
    }

    test("ensureNotNull - value is null, binds error") {
      val result = binding {
        ensureNotNull<String, Error>(null) { someError }
        fail("Should not reach here")
      }

      result.shouldBe(Err(someError))
    }
  }

  context("non suspend binding") {
    test("ensure - predicate is true, does not bind error") {
      val result = binding {
        ensure(true) { someError }
      }

      result.shouldBe(Ok(Unit))
    }

    test("ensure - predicate is false, binds error") {
      val result = binding {
        ensure(false) { someError }
      }

      result.shouldBe(Err(someError))
    }

    test("ensureNotNull - value is not null, does not bind error") {
      @Suppress("RedundantNullableReturnType")
      val result = binding {
        val nullableValue: String? = "value"
        val nonNullableValue = ensureNotNull(nullableValue) { someError }
        // Referenced to test type inference
        nonNullableValue.length.shouldBe(5)
        nonNullableValue.shouldBe(nullableValue)
      }

      result.shouldBe(Ok("value"))
    }

    test("ensureNotNull - value is null, binds error") {
      val result = binding {
        ensureNotNull<String, Error>(null) { someError }
        fail("Should not reach here")
      }

      result.shouldBe(Err(someError))
    }
  }
})
