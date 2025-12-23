@file:OptIn(DelicateCoroutinesApi::class)

package build.wallet.coroutines.turbine

import app.cash.turbine.Turbine
import io.kotest.assertions.shouldFailWithMessage
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
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

  context("awaitUntil") {
    test("next emitted item matches expected item") {
      val turbine = Turbine<Any>()

      turbine.add("foo")
      turbine.add("bar")
      turbine.add(100)

      turbine.awaitUntil("foo").shouldBe("foo")
      // Do not skip subsequent items
      turbine.awaitItem().shouldBe("bar")
      turbine.awaitItem().shouldBe(100)
      turbine.expectNoEvents()
    }

    test("skim intermediate non matching items") {
      val turbine = Turbine<Any>()

      turbine.add("foo")
      turbine.add("bar")
      turbine.add(100)

      turbine.awaitUntil(100).shouldBe(100)
      turbine.expectNoEvents()
    }

    test("multiple next items match expected item") {
      val turbine = Turbine<Any>()

      turbine.add("foo")
      turbine.add("foo")
      turbine.add("foo")

      turbine.awaitUntil("foo").shouldBe("foo")
      turbine.awaitUntil("foo").shouldBe("foo")
      turbine.awaitUntil("foo").shouldBe("foo")
      turbine.expectNoEvents()
    }

    test("no matching items") {
      val turbine = Turbine<Any>(timeout = 10.milliseconds)

      turbine.add("foo")
      turbine.add("bar")
      turbine.add(100)

      shouldFailWithMessage(message = "No value produced in 10ms") {
        turbine.awaitUntil("fuzz")
      }
    }
  }

  context("awaitUntilNotNull") {
    test("next emitted is not null") {
      val turbine = Turbine<Any?>()

      turbine.add("foo")
      turbine.add("bar")
      turbine.add(100)

      turbine.awaitUntilNotNull().shouldBe("foo")
      // Do not skip subsequent items
      turbine.awaitItem().shouldBe("bar")
      turbine.awaitItem().shouldBe(100)
      turbine.expectNoEvents()
    }

    test("skim intermediate null items") {
      val turbine = Turbine<Any?>()

      turbine.add(null)
      turbine.add(null)
      turbine.add(100)

      turbine.awaitUntil(100).shouldBe(100)
      turbine.expectNoEvents()
    }

    test("multiple next items are not null") {
      val turbine = Turbine<Any?>()

      turbine.add("foo")
      turbine.add("foo")
      turbine.add("foo")

      turbine.awaitUntil("foo").shouldBe("foo")
      turbine.awaitUntil("foo").shouldBe("foo")
      turbine.awaitUntil("foo").shouldBe("foo")
      turbine.expectNoEvents()
    }

    test("all items are null") {
      val turbine = Turbine<Any?>(timeout = 10.milliseconds)

      turbine.add(null)
      turbine.add(null)
      turbine.add(null)

      shouldFailWithMessage(message = "No value produced in 10ms") {
        turbine.awaitUntilNotNull()
      }
    }
  }

  context("awaitNoEvents") {
    test("should not throw when there are no events at all") {
      val turbine = Turbine<Any>()

      // Should not throw
      turbine.awaitNoEvents(timeout = 10.milliseconds)
    }

    test("should not throw when there are no events after a specific timeout") {
      val turbine = Turbine<Any>()

      launch {
        delay(20.milliseconds)
        turbine.add(1)
      }

      // Should not throw
      turbine.awaitNoEvents(timeout = 10.milliseconds)
    }

    test("should throw when there is an event before timeout") {
      val turbine = Turbine<Any>()
      turbine.add(1)

      shouldFailWithMessage(message = "Expected no events but found Item(1)") {
        turbine.awaitNoEvents(timeout = 1.milliseconds)
      }
    }
  }

  context("awaitItemMaybe") {
    test("returns item when emitted before timeout") {
      val turbine = Turbine<String>()
      turbine.add("foo")

      turbine.awaitItemMaybe(timeout = 100.milliseconds).shouldBe("foo")
      turbine.expectNoEvents()
    }

    test("returns null when no item emitted within timeout") {
      val turbine = Turbine<String>()

      turbine.awaitItemMaybe(timeout = 10.milliseconds).shouldBe(null)
    }

    test("returns null when item emitted after timeout") {
      val turbine = Turbine<String>()

      launch {
        delay(50.milliseconds)
        turbine.add("foo")
      }

      turbine.awaitItemMaybe(timeout = 10.milliseconds).shouldBe(null)
    }

    test("consumes item from turbine when present") {
      val turbine = Turbine<String>()
      turbine.add("first")

      turbine.awaitItemMaybe(timeout = 100.milliseconds).shouldBe("first")
      turbine.expectNoEvents()
    }

    test("returns most recent item when multiple present") {
      val turbine = Turbine<String>()
      turbine.add("first")
      turbine.add("second")

      // Returns most recent and consumes all items - appropriate for race condition handling
      // where we just need to know if any event was emitted
      turbine.awaitItemMaybe(timeout = 100.milliseconds).shouldBe("second")
      turbine.expectNoEvents()
    }

    test("does not affect turbine when no item present") {
      val turbine = Turbine<String>()

      turbine.awaitItemMaybe(timeout = 10.milliseconds).shouldBe(null)

      // Turbine should still work normally after
      turbine.add("foo")
      turbine.awaitItem().shouldBe("foo")
      turbine.expectNoEvents()
    }
  }
})
