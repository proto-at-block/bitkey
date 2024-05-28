package build.wallet.ui.framework

import app.cash.turbine.test
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldNotBeInstanceOf
import kotlinx.coroutines.flow.MutableStateFlow

class NavigatorImplTests : FunSpec({
  val screenA = ScreenFake(id = "A")
  val screenB = ScreenFake(id = "B")

  fun navigator() = NavigatorImpl(screenA)

  test("currentScreen is not mutable") {
    navigator().currentScreen.shouldNotBeInstanceOf<MutableStateFlow<*>>()
  }

  test("currentScreen is initially the initial screen") {
    navigator().currentScreen.test {
      awaitItem().shouldBe(screenA)
    }
  }

  test("currentScreen is updated when navigating to a different screen") {
    val navigator = navigator()

    navigator.currentScreen.test {
      awaitItem().shouldBe(screenA)

      navigator.goTo(screenB)
      awaitItem().shouldBe(screenB)
    }
  }

  test("currentScreen is the same when navigating to the same screen") {
    val navigator = navigator()

    navigator.currentScreen.test {
      awaitItem().shouldBe(screenA)

      navigator.goTo(screenA)
      expectNoEvents()
      navigator.currentScreen.value.shouldBe(screenA)
    }
  }

  test("can navigate back to the previous screen") {
    val navigator = navigator()

    navigator.currentScreen.test {
      awaitItem().shouldBe(screenA)

      navigator.goTo(screenB)
      awaitItem().shouldBe(screenB)

      navigator.goTo(screenA)
      awaitItem().shouldBe(screenA)
    }
  }
})
