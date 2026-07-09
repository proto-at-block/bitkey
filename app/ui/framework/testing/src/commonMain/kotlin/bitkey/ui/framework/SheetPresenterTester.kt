package bitkey.ui.framework

import app.cash.molecule.RecompositionMode.Immediate
import app.cash.molecule.moleculeFlow
import app.cash.turbine.ReceiveTurbine
import app.cash.turbine.Turbine
import app.cash.turbine.test
import build.wallet.coroutines.withTimeoutThrowing
import build.wallet.statemachine.core.SheetModel
import io.kotest.assertions.withClue
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.onCompletion
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * Testing API for [SheetPresenter]s — mirrors [ScreenPresenter.test].
 *
 * Collects and validates [SheetModel]s produced by the [SheetPresenter].
 *
 * Validation block provides a [NavigatorMock] which can be used to deterministically verify
 * navigation events. Unconsumed navigation events will fail the test.
 *
 * Example usage:
 * ```
 * presenter.test(ViewInvitationSheet(...)) { navigator ->
 *   awaitSheetBody<ViewingInvitationBodyModel> {
 *     primaryButton.shouldNotBeNull().onClick.invoke()
 *   }
 *   navigator.goToCalls.awaitItem().shouldBe(SomeScreen)
 * }
 * ```
 */
@OptIn(DelicateCoroutinesApi::class)
suspend fun <SheetT : Sheet> SheetPresenter<SheetT>.test(
  sheet: SheetT,
  testTimeout: Duration = 10.seconds,
  modelTimeout: Duration = 3.seconds,
  validate: suspend ReceiveTurbine<SheetModel>.(NavigatorMock) -> Unit,
) {
  val navigator = NavigatorMock(turbine = { Turbine() })

  withTimeoutThrowing(testTimeout) {
    val dispatcher = singleThreadedDispatcher()
    val models = moleculeFlow(Immediate) { model(navigator, sheet) }
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
