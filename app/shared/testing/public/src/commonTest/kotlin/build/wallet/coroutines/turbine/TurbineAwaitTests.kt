package build.wallet.coroutines.turbine

import app.cash.turbine.Turbine
import io.kotest.assertions.shouldFailWithMessage
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import kotlin.time.Duration.Companion.milliseconds

class TurbineAwaitTests : FunSpec({

  context("awaitUntil - matching predicate") {
    test("next emitted item matches predicate") {
      val turbine = Turbine<Any>()

      turbine.add("foo")
      turbine.add("bar")
      turbine.add(100)

      turbine.awaitUntil { it == "foo" }.shouldBe("foo")
      // Do not skip subsequent items
      turbine.awaitItem().shouldBe("bar")
      turbine.awaitItem().shouldBe(100)
      turbine.expectNoEvents()
    }

    test("first few emitted items do not match type") {
      val turbine = Turbine<Any>()

      turbine.add(1)
      turbine.add(1f)
      turbine.add("foo")
      turbine.add("bar")
      turbine.add(100)

      turbine.awaitUntil { it == "foo" }.shouldBe("foo")
      // Do not skip subsequent items
      turbine.awaitItem().shouldBe("bar")
      turbine.awaitItem().shouldBe(100)
      turbine.expectNoEvents()
    }

    test("no items matching type") {
      val turbine = Turbine<Any>(timeout = 100.milliseconds)

      turbine.add(1)
      turbine.add(1f)

      shouldFailWithMessage(message = "No value produced in 100ms") {
        turbine.awaitUntil { it == "foo" }
      }
    }
  }

  context("awaitUntil - matching type") {
    test("next emitted item matches predicate") {
      val turbine = Turbine<Any>()

      turbine.add("foo")
      turbine.add("bar")
      turbine.add(100)

      turbine.awaitUntil<String>().shouldBe("foo")
      // Do not skip subsequent items
      turbine.awaitItem().shouldBe("bar")
      turbine.awaitItem().shouldBe(100)
      turbine.expectNoEvents()
    }

    test("first few emitted items do not match type") {
      val turbine = Turbine<Any>()

      turbine.add(1)
      turbine.add(1f)
      turbine.add("foo")
      turbine.add("bar")
      turbine.add(100)

      turbine.awaitUntil<String>().shouldBe("foo")
      // Do not skip subsequent items
      turbine.awaitItem().shouldBe("bar")
      turbine.awaitItem().shouldBe(100)
      turbine.expectNoEvents()
    }

    test("no items matching type") {
      val turbine = Turbine<Any>(timeout = 100.milliseconds)

      turbine.add(1)
      turbine.add(1f)

      shouldFailWithMessage(message = "No value produced in 100ms") {
        turbine.awaitUntil<String>()
      }
    }
  }
})
