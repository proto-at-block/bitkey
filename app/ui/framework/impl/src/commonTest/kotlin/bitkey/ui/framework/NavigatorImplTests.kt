package bitkey.ui.framework

import app.cash.turbine.test
import build.wallet.coroutines.turbine.turbines
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldNotBeInstanceOf
import kotlinx.coroutines.flow.MutableStateFlow

class NavigatorImplTests : FunSpec({
  val screenA = ScreenFake(id = "A")
  val screenB = ScreenFake(id = "B")

  val sheetA = SheetFake(id = "Sheet A", origin = screenA)

  val exitCalls = turbines.create<Unit>("exit calls")

  fun navigator() =
    NavigatorImpl(
      initialScreen = screenA,
      // Collect exit calls into Turbine to make sure we deterministically test them.
      onExit = { exitCalls.add(Unit) }
    )

  test("currentScreen is not mutable") {
    navigator().currentScreenState.shouldNotBeInstanceOf<MutableStateFlow<*>>()
  }

  test("currentScreen is initially the initial screen") {
    navigator().currentScreenState.test {
      awaitItem().apply {
        screen.shouldBe(screenA)
      }
    }
  }

  test("currentScreen is updated when navigating to a different screen") {
    val navigator = navigator()

    navigator.currentScreenState.test {
      awaitItem().apply {
        screen.shouldBe(screenA)
      }

      navigator.goTo(screenB)
      awaitItem().apply {
        screen.shouldBe(screenB)
        sheet.shouldBeNull()
      }
    }
  }

  test("currentScreen is the same when navigating to the same screen") {
    val navigator = navigator()

    navigator.currentScreenState.test {
      awaitItem().screen.shouldBe(screenA)

      navigator.goTo(screenA)
      expectNoEvents()
      navigator.currentScreenState.value.screen.shouldBe(screenA)
    }
  }

  test("can navigate back to the previous screen") {
    val navigator = navigator()

    navigator.currentScreenState.test {
      awaitItem().screen.shouldBe(screenA)

      navigator.goTo(screenB)
      awaitItem().screen.shouldBe(screenB)

      navigator.goTo(screenA)
      awaitItem().screen.shouldBe(screenA)
    }
  }

  test("exit navigator from initial screen") {
    val navigator = navigator()

    navigator.exit()

    exitCalls.awaitItem()
  }

  test("exit navigator after some navigation") {
    val navigator = navigator()

    navigator.currentScreenState.test {
      awaitItem().screen.shouldBe(screenA)

      navigator.goTo(screenB)
      awaitItem().screen.shouldBe(screenB)

      navigator.exit()

      exitCalls.awaitItem()
    }
  }
})
