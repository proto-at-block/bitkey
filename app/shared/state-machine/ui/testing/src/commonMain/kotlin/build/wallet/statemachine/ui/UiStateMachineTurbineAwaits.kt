package build.wallet.statemachine.ui

import app.cash.turbine.ReceiveTurbine
import build.wallet.analytics.events.screen.id.EventTrackerScreenId
import build.wallet.coroutines.turbine.awaitUntil
import build.wallet.logging.log
import build.wallet.statemachine.core.BodyModel
import build.wallet.statemachine.core.ScreenModel
import build.wallet.statemachine.core.form.FormBodyModel
import io.kotest.assertions.asClue
import io.kotest.assertions.assertSoftly

suspend inline fun <reified T : BodyModel> ReceiveTurbine<ScreenModel>.awaitUntilScreenWithBody(
  id: EventTrackerScreenId? = null,
  crossinline expectedBodyContentMatch: (T) -> Boolean = { _ -> true },
  block: T.() -> Unit = {},
): T {
  val body =
    awaitUntilScreenModelWithBody<T>(id, expectedBodyContentMatch) {
      block(body as T)
    }.body as T
  return body
}

suspend inline fun <reified T : BodyModel> ReceiveTurbine<ScreenModel>.awaitUntilScreenModelWithBody(
  id: EventTrackerScreenId? = null,
  crossinline expectedBodyContentMatch: (T) -> Boolean = { _ -> true },
  crossinline expectedScreenModelMatch: (ScreenModel) -> Boolean = { _ -> true },
  block: ScreenModel.() -> Unit = {},
): ScreenModel {
  log { "Waiting for ScreenModel(${T::class.simpleName}) id=$id" }

  // Models that were previously seen but do not match predicate. Used for debugging.
  val previousModels = mutableListOf<ScreenModel>()

  val screen =
    try {
      awaitUntil {
        log { "Saw ${it.toSimpleString()}" }
        val matches =
          it.body is T &&
            (id == null || it.body.eventTrackerScreenInfo?.eventTrackerScreenId == id) &&
            expectedBodyContentMatch(it.body as T) &&
            expectedScreenModelMatch(it)
        if (!matches) previousModels += it
        matches
      }
    } catch (e: AssertionError) {
      val previousModelsMessage = "Previous models: ${previousModels.map { it.toSimpleString() }}}"
      val message =
        if (id != null) {
          "Did not see expected ScreenModel(${T::class.simpleName} id=$id). $previousModelsMessage"
        } else {
          "Did not see expected ScreenModel(${T::class.simpleName}). $previousModelsMessage"
        }
      throw AssertionError(message, e)
    }
  screen.body.asClue {
    assertSoftly {
      block(screen)
    }
  }
  return screen
}

inline fun ScreenModel.toSimpleString(): String {
  return "ScreenModel(${body::class.simpleName}) id=${body.eventTrackerScreenInfo?.eventTrackerScreenId}"
}

/**
 * Awaits for a [ScreenModel] with a [FormBodyModel], exact screen ID and body content match.
 */
suspend fun ReceiveTurbine<ScreenModel>.formScreen(
  id: EventTrackerScreenId,
  match: FormBodyModel.() -> Boolean = { true },
  validate: FormBodyModel.() -> Unit,
): FormBodyModel =
  awaitUntilScreenWithBody<FormBodyModel>(
    id = id,
    expectedBodyContentMatch = match,
    block = validate
  )
