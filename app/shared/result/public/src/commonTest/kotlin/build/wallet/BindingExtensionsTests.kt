package build.wallet

import com.github.michaelbull.result.Err
import com.github.michaelbull.result.Ok
import com.github.michaelbull.result.binding
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import com.github.michaelbull.result.coroutines.binding.binding as suspendBinding

class BindingExtensionsTests : FunSpec({

  val someError = Error("test")

  context("suspendBinding") {
    test("ensure - predicate is true, does not bind error") {
      val result = suspendBinding {
        ensure(true) { someError }
      }

      result.shouldBe(Ok(Unit))
    }

    test("ensure - predicate is false, binds error") {

      val result = suspendBinding {
        ensure(false) { someError }
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
  }
})
