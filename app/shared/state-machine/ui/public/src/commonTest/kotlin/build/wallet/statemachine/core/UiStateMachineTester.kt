package build.wallet.statemachine.core

import app.cash.turbine.ReceiveTurbine
import build.wallet.analytics.events.screen.id.EventTrackerScreenId
import build.wallet.statemachine.BodyModelMock
import build.wallet.ui.model.Model
import io.kotest.assertions.asClue
import io.kotest.assertions.assertSoftly
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.kotest.matchers.types.shouldNotBeInstanceOf

/**
 * Awaits for a body model of type [T] for the [ScreenModel] Turbine receiver.
 * Executes [block] with the given [T] model.
 */
suspend inline fun <reified T : BodyModel> ReceiveTurbine<ScreenModel>.awaitScreenWithBody(
  id: EventTrackerScreenId? = null,
  block: T.() -> Unit = {},
) {
  awaitItem().body.let { body ->
    body.asClue {
      body.shouldBeInstanceOf<T>()

      body.shouldNotBeInstanceOf<BodyModelMock<*>>()

      if (id != null) {
        body.eventTrackerScreenInfo
          .shouldNotBeNull()
          .eventTrackerScreenId
          .shouldBe(id)
      }

      assertSoftly {
        block(body)
      }
    }
  }
}

suspend inline fun <reified T : Any> ReceiveTurbine<ScreenModel>.awaitScreenWithBodyModelMock(
  id: String? = null,
  block: T.() -> Unit = {},
) {
  awaitItem().body.let { body ->
    body.asClue {
      body.shouldBeInstanceOf<BodyModelMock<T>>()
      body.latestProps.shouldBeInstanceOf<T>()
      id?.let {
        body.id.shouldBe(it)
      }
      assertSoftly {
        block(body.latestProps)
      }
    }
  }
}

/**
 * Awaits for a model of type [T] for the [BodyModel] Turbine receiver.
 * Executes [block] with the given [T] model.
 */
suspend inline fun <reified T : Model> ReceiveTurbine<BodyModel>.awaitBody(
  block: T.() -> Unit = {},
) {
  awaitItem().let { body ->
    body.asClue {
      body.shouldBeInstanceOf<T>()
      assertSoftly {
        block(body)
      }
    }
  }
}

/**
 * Awaits for a model of type [T] for the [SheetModel] Turbine receiver.
 * Executes [block] with the given [T] model.
 */
suspend inline fun <reified T : Model> ReceiveTurbine<SheetModel>.awaitSheetWithBody(
  block: T.() -> Unit = {},
) {
  awaitItem().body.let { body ->
    body.asClue {
      body.shouldBeInstanceOf<T>()
      assertSoftly {
        block(body)
      }
    }
  }
}

suspend inline fun <reified T : BodyModel> ReceiveTurbine<ScreenModel>.awaitScreenWithSheetModelBody(
  id: EventTrackerScreenId? = null,
  block: T.() -> Unit = {},
) {
  awaitItem().bottomSheetModel?.asClue { sheetModel ->
    sheetModel.body.shouldBeInstanceOf<T>()

    sheetModel.body.shouldNotBeInstanceOf<BodyModelMock<*>>()

    if (id != null) {
      sheetModel.body.eventTrackerScreenInfo
        .shouldNotBeNull()
        .eventTrackerScreenId
        .shouldBe(id)
    }

    assertSoftly {
      block(sheetModel.body as T)
    }
  }
}
