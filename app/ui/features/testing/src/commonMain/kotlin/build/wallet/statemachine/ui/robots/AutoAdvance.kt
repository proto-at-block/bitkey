package build.wallet.statemachine.ui.robots

import app.cash.turbine.ReceiveTurbine
import build.wallet.analytics.events.screen.id.EventTrackerScreenId
import build.wallet.statemachine.automations.AutomaticUiTests
import build.wallet.statemachine.automations.AutomationUnavailable
import build.wallet.statemachine.core.BodyModel
import build.wallet.statemachine.core.ScreenModel
import io.kotest.assertions.failure
import io.kotest.assertions.withClue
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.isActive

/**
 * Automatically advances to the next screen until a condition is met.
 *
 * This function uses the [AutomaticUiTests] interface to follow a primary
 * path in the application. It will continue going down the primary path
 * screen by screen until the [stopCondition] is met.
 *
 * This is used to advance through screens in the application that are not
 * particularly relevant to the test case, and keeps the test generic
 * and less brittle to changes.
 */
suspend fun ReceiveTurbine<ScreenModel>.advanceUntil(
  id: EventTrackerScreenId? = null,
  stopCondition: (ScreenModel) -> Boolean,
  assertions: ScreenModel.() -> Unit = {},
): ScreenModel {
  while (currentCoroutineContext().isActive) {
    withClue("Auto-advancing through Form Screens") {
      val next = awaitItem()
      val body = next.body
      val idMatch = (id == null || next.body.eventTrackerScreenInfo?.eventTrackerScreenId == id)
      when {
        idMatch && stopCondition(next) -> return next.also {
          next.assertions()
        }
        body is AutomaticUiTests -> try {
          body.automateNextPrimaryScreen()
        } catch (e: AutomationUnavailable) {
          throw failure(e.reason)
        }
        else -> {
          throw failure("Unable to auto-advance screen with body type ${body::class.simpleName}")
        }
      }
    }
  }
  throw failure("Coroutine completed before advancing could complete")
}

/**
 * Advances until a screen with a specified body type is reached.
 *
 * Optionally, the [stopCondition] can be used to further filter advancements.
 */
suspend inline fun <reified T : BodyModel> ReceiveTurbine<ScreenModel>.advanceUntilScreenWithBody(
  id: EventTrackerScreenId? = null,
  crossinline stopCondition: (T) -> Boolean = { true },
  crossinline assertions: T.() -> Unit = {},
): T {
  return advanceUntil(
    id = id,
    stopCondition = { it.body is T && stopCondition(it.body as T) }
  ) {
    (body as T).assertions()
  }.body as T
}
