package bitkey.ui.framework

import app.cash.molecule.RecompositionMode.Immediate
import app.cash.molecule.moleculeFlow
import app.cash.turbine.ReceiveTurbine
import app.cash.turbine.Turbine
import app.cash.turbine.test
import build.wallet.coroutines.withTimeoutThrowing
import build.wallet.platform.random.uuid
import build.wallet.statemachine.core.ScreenModel
import io.kotest.assertions.withClue
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.newSingleThreadContext
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * Testing API for [ScreenPresenter]s.
 *
 * Collects and validates [ScreenModel]s produced by [ScreenPresenter].
 *
 * Validation block provides a [NavigatorMock] which can be used to deterministically
 * verify navigation events. Unconsumed navigation events will fail the test.
 *
 * Example usage:
 * ```
 * presenter.test(SubmitFeedbackFormScreen(form, data)) { navigator ->
 *   awaitLoadingScreen(FEEDBACK_SUBMITTING)
 *
 *   navigator.goToCalls.awaitItem()
 *     .shouldBe(FeedbackFormSubmittedScreen)
 *  }
 * ```
 *
 * @param [screen] [ScreenT] to use for the presenter.
 * @param [testTimeout] timeout duration for this [test] to complete.
 * @param [modelTimeout] timeout duration for each [ScreenModel] to be emitted.
 * @param [validate] validation block with [ReceiveTurbine] to validate emitted [ScreenModel]s
 * and [NavigatorMock] to validate navigation events.
 */
suspend fun <ScreenT : Screen> ScreenPresenter<ScreenT>.test(
  screen: ScreenT,
  testTimeout: Duration = 10.seconds,
  modelTimeout: Duration = 3.seconds,
  validate: suspend ReceiveTurbine<ScreenModel>.(NavigatorMock) -> Unit,
) {
  // We are not using `TurbineExtension` here to reduce test boilerplate. Instead, unconsumed
  // Navigator events are validated at the end of the `test` call below.
  val navigator = NavigatorMock(turbine = { Turbine() })

  withTimeoutThrowing(testTimeout) {
    val dispatcher = singleThreadedDispatcher()
    val models = moleculeFlow(Immediate) { model(navigator, screen) }
      .flowOn(dispatcher)
      .onCompletion { dispatcher.cancel() }
      .distinctUntilChanged()

    models.test(modelTimeout) {
      validate(navigator)
    }
  }
  withClue("Unconsumed Navigator events") {
    navigator.goToCalls.expectNoEvents()
    navigator.exitCalls.expectNoEvents()
    navigator.showSheetCalls.expectNoEvents()
    navigator.closeSheetCalls.expectNoEvents()
  }
}

/**
 * Single-threaded dispatcher used for testing `StateMachine` compositions.
 * Ensures deterministic, sequential execution (mimicking the UI thread used in Android and iOS app),
 * and prevents concurrency issues or flaky behavior from parallel recompositions.
 *
 * Only meant for internal `StateMachineTester` implementation. Public to keep `test` extensions
 * inline.
 */
@DelicateCoroutinesApi
fun singleThreadedDispatcher(): CoroutineDispatcher =
  newSingleThreadContext("ScreenPresenter.test-${uuid()}")
