package bitkey.ui.framework

import app.cash.molecule.RecompositionMode
import app.cash.molecule.moleculeFlow
import app.cash.turbine.TurbineTestContext
import app.cash.turbine.test
import build.wallet.statemachine.core.ScreenModel
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeTypeOf
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

class NavigatorPresenterImplTests : FunSpec({

  val screenPresenter = ScreenPresenterFake()
  val screenA = ScreenFake(id = "A")
  val screenB = ScreenFake(id = "B")

  val screenPresenterRegistry = object : ScreenPresenterRegistry {
    override fun <ScreenT : Screen> get(screen: ScreenT): ScreenPresenter<ScreenT> {
      @Suppress("UNCHECKED_CAST")
      when (screen) {
        is ScreenFake -> return screenPresenter as ScreenPresenter<ScreenT>
      }

      error("Unknown screen: ${screen::class.simpleName}")
    }
  }
  val navigatorPresenter = NavigatorPresenterImpl(screenPresenterRegistry)

  test("model for initial screen is produced") {
    navigatorPresenter.test(
      initialScreen = screenA
    ) {
      awaitItem().body.shouldBeTypeOf<NavigatingBodyModelFake>().run {
        id.shouldBe("A")
      }
    }
  }

  test("model for different screen is produced on navigation") {
    navigatorPresenter.test(initialScreen = screenA) {
      awaitItem().body.shouldBeTypeOf<NavigatingBodyModelFake>().run {
        id.shouldBe("A")
        goTo(screenB)
      }

      awaitItem().body.shouldBeTypeOf<NavigatingBodyModelFake>().run {
        id.shouldBe("B")
      }
    }
  }

  test("navigating to the same screen does not produce a new model") {
    navigatorPresenter.test(initialScreen = screenA) {
      awaitItem().body.shouldBeTypeOf<NavigatingBodyModelFake>().run {
        id.shouldBe("A")
        goTo(screenA)
      }

      expectNoEvents()
    }
  }

  test("navigating to previous screen produces a new model") {
    navigatorPresenter.test(initialScreen = screenA) {
      awaitItem().body.shouldBeTypeOf<NavigatingBodyModelFake>().run {
        id.shouldBe("A")
        goTo(screenB)
      }

      awaitItem().body.shouldBeTypeOf<NavigatingBodyModelFake>().run {
        id.shouldBe("B")
        goTo(screenA)
      }

      awaitItem().body.shouldBeTypeOf<NavigatingBodyModelFake>().run {
        id.shouldBe("A")
      }
    }
  }
})

private suspend fun NavigatorPresenter.test(
  initialScreen: Screen,
  turbineTimeout: Duration = 3.seconds,
  validate: suspend TurbineTestContext<ScreenModel>.() -> Unit,
) {
  val models: Flow<ScreenModel> = moleculeFlow(mode = RecompositionMode.Immediate) {
    model(initialScreen, {})
  }.distinctUntilChanged()

  models.test(timeout = turbineTimeout) {
    validate()
  }
}
