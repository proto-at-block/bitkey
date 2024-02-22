package build.wallet.coroutines.turbine

import app.cash.turbine.ReceiveTurbine
import app.cash.turbine.Turbine
import io.kotest.assertions.shouldFailWithMessage
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

class TurbineTransformersTests : FunSpec({
  context("expectingTypeOf - require emitted items to be of specific type") {
    val originalTurbine = Turbine<Any>()
    val transformedTurbine: ReceiveTurbine<Int> = originalTurbine.withTypeOf<Int>()

    test("consume item suspending") {
      originalTurbine.add(1)
      transformedTurbine.awaitItem().shouldBe(1)
    }

    test("consume item non-suspending") {
      originalTurbine.add(2)
      // Non-suspending
      transformedTurbine.expectMostRecentItem().shouldBe(2)
    }

    test("consume from original turbine - transformed turbine does not emit") {
      originalTurbine.add(3)
      originalTurbine.awaitItem().shouldBe(3)
      transformedTurbine.expectNoEvents()
    }

    test("emit item of the wrong type and fail") {
      originalTurbine.add(4.1)
      shouldFailWithMessage(message = "4.1 should be of type kotlin.Int") {
        transformedTurbine.awaitItem()
      }
    }

    // All events consumed
    transformedTurbine.expectNoEvents()
    originalTurbine.expectNoEvents()
  }

  context("map - transform emitted items") {
    val originalTurbine = Turbine<Int>()
    val transformedTurbine: ReceiveTurbine<String> = originalTurbine.map { it.toString() }

    test("consume item suspending") {
      originalTurbine.add(1)
      transformedTurbine.awaitItem().shouldBe("1")
    }

    test("consume item non-suspending") {
      originalTurbine.add(2)
      transformedTurbine.expectMostRecentItem().shouldBe("2")
    }

    test("consume from original turbine - transformed turbine does not emit") {
      originalTurbine.add(3)
      originalTurbine.awaitItem().shouldBe(3)
      transformedTurbine.expectNoEvents()
    }

    // All events consumed
    transformedTurbine.expectNoEvents()
    originalTurbine.expectNoEvents()
  }
})
