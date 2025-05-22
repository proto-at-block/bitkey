package build.wallet.statemachine.ui

import app.cash.turbine.ReceiveTurbine
import build.wallet.analytics.events.screen.id.EventTrackerScreenId
import build.wallet.coroutines.turbine.awaitUntil
import build.wallet.logging.logTesting
import build.wallet.statemachine.BodyModelMock
import build.wallet.statemachine.core.BodyModel
import build.wallet.statemachine.core.ScreenModel
import build.wallet.statemachine.core.SheetModel
import build.wallet.ui.model.Model
import io.kotest.assertions.asClue
import io.kotest.assertions.assertSoftly
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.kotest.matchers.types.shouldNotBeInstanceOf
import kotlin.jvm.JvmName

/**
 * Awaits for a body model of type [T] for the [ScreenModel] Turbine receiver.
 * Executes [validate] with the given [T] model.
 */
suspend inline fun <reified T : BodyModel> ReceiveTurbine<ScreenModel>.awaitBody(
  id: EventTrackerScreenId? = null,
  validate: T.() -> Unit = {},
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
        validate(body)
      }
    }
  }
}

suspend inline fun <reified T : Any> ReceiveTurbine<ScreenModel>.awaitBodyMock(
  id: String? = null,
  validate: T.() -> Unit = {},
) {
  awaitItem().body.let { body ->
    body.asClue {
      body.shouldBeInstanceOf<BodyModelMock<T>>()
      body.latestProps.shouldBeInstanceOf<T>()
      id?.let {
        body.id.shouldBe(it)
      }
      assertSoftly {
        validate(body.latestProps)
      }
    }
  }
}

suspend inline fun <reified PropsT : Any> ReceiveTurbine<ScreenModel>.awaitUntilBodyMock(
  id: String? = null,
  validate: PropsT.() -> Unit,
): PropsT {
  val body = awaitUntilScreenWithBody<BodyModelMock<PropsT>>(matchingBody = { body ->
    body.shouldBeInstanceOf<BodyModelMock<PropsT>>()
    body.latestProps.shouldBeInstanceOf<PropsT>()
    id?.let {
      body.id.shouldBe(it)
    }
    true
  }).body as BodyModelMock<PropsT>
  validate(body.latestProps)
  return body.latestProps
}

/**
 * Awaits for a model of type [T] for the [BodyModel] Turbine receiver.
 * Executes [validate] with the given [T] model.
 */
suspend inline fun <reified T : Model> ReceiveTurbine<BodyModel>.awaitBody(
  validate: T.() -> Unit = {},
) {
  awaitItem().let { body ->
    body.asClue {
      body.shouldBeInstanceOf<T>()
      assertSoftly {
        validate(body)
      }
    }
  }
}

suspend inline fun <reified T : BodyModel> ReceiveTurbine<ScreenModel>.awaitUntilBody(
  id: EventTrackerScreenId? = null,
  crossinline matching: (T) -> Boolean = { true },
  validate: T.() -> Unit = {},
): T {
  val body = awaitUntilScreenWithBody<T>(id, matching) {
    validate(body as T)
  }.body as T
  return body
}

suspend inline fun <reified T : BodyModel> ReceiveTurbine<BodyModel>.awaitUntilBodyModel(
  id: EventTrackerScreenId? = null,
  crossinline matching: (T) -> Boolean = { true },
  validate: T.() -> Unit = {},
): T {
  // Models that were previously seen but do not match predicate. Used for debugging.
  val previousModels = mutableListOf<BodyModel>()

  val body = try {
    awaitUntil {
      logTesting { "Saw ${it.toSimpleString()}" }
      val matches = it is T &&
        (id == null || it.eventTrackerScreenInfo?.eventTrackerScreenId == id) &&
        matching(it)
      if (!matches) previousModels += it
      matches
    } as T
  } catch (e: AssertionError) {
    val previousModelsMessage = "Previous models: ${previousModels.map { it.toSimpleString() }}"
    val message = if (id != null) {
      "Did not see expected BodyModel(${T::class.simpleName} id=$id). $previousModelsMessage"
    } else {
      "Did not see expected BodyModel(${T::class.simpleName}). $previousModelsMessage"
    }
    throw AssertionError(message, e)
  }
  body.asClue {
    assertSoftly {
      validate(body)
    }
  }
  return body
}

@JvmName("awaitSheetFromScreenModelTurbine")
suspend inline fun <reified T : BodyModel> ReceiveTurbine<ScreenModel>.awaitSheet(
  validate: T.() -> Unit = {},
) {
  awaitItem().bottomSheetModel?.asClue { sheetModel ->
    sheetModel.body.shouldBeInstanceOf<T>()
    sheetModel.body.shouldNotBeInstanceOf<BodyModelMock<*>>()

    assertSoftly {
      validate(sheetModel.body as T)
    }
  }
}

/**
 * Awaits for a model of type [T] for the [SheetModel] Turbine receiver.
 * Executes [validate] with the given [T] model.
 */
@JvmName("awaitSheetFromSheetModelTurbine")
suspend inline fun <reified T : BodyModel> ReceiveTurbine<SheetModel>.awaitSheet(
  validate: T.() -> Unit = {},
) {
  awaitItem().asClue { sheetModel ->
    sheetModel.body.shouldBeInstanceOf<T>()
    sheetModel.body.shouldNotBeInstanceOf<BodyModelMock<*>>()

    assertSoftly {
      validate(sheetModel.body as T)
    }
  }
}

suspend inline fun <reified T : BodyModel> ReceiveTurbine<ScreenModel>.awaitUntilScreenWithBody(
  id: EventTrackerScreenId? = null,
  crossinline matchingBody: (T) -> Boolean = { _ -> true },
  crossinline matchingScreen: (ScreenModel) -> Boolean = { _ -> true },
  validate: ScreenModel.() -> Unit = {},
): ScreenModel {
  // Models that were previously seen but do not match predicate. Used for debugging.
  val previousModels = mutableListOf<ScreenModel>()

  val screen =
    try {
      awaitUntil {
        logTesting { "Saw ${it.toSimpleString()}" }
        val matches =
          it.body is T &&
            (id == null || it.body.eventTrackerScreenInfo?.eventTrackerScreenId == id) &&
            matchingBody(it.body as T) &&
            matchingScreen(it)
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
      validate(screen)
    }
  }
  return screen
}

inline fun ScreenModel.toSimpleString(): String {
  return buildString {
    append("ScreenModel(")
    append(body.toSimpleString())
    bottomSheetModel?.run {
      append(", bottomSheetModel=SheetModel(body=${body::class.simpleName})")
    }
    append(")")
  }
}

inline fun BodyModel.toSimpleString(): String {
  return buildString {
    val bodyName = this@toSimpleString::class.simpleName
    append("body=$bodyName")
    // TODO(W-9780): remove this once FormBodyModelImpl is removed.
    if (bodyName == "FormBodyModelImpl") {
      // not an exact FormBodyModel type, so add screen ID as a hint
      append(" id=${eventTrackerScreenInfo?.eventTrackerScreenId}")
    }
  }
}

/**
 * Await multiple screens until one is seen with a matching sheet model.
 */
suspend inline fun <reified T : BodyModel> ReceiveTurbine<ScreenModel>.awaitUntilSheet(
  id: EventTrackerScreenId? = null,
  crossinline matching: (T) -> Boolean = { _ -> true },
  validate: T.() -> Unit = {},
) {
  // Models that were previously seen but do not match predicate. Used for debugging.
  val previousModels = mutableListOf<ScreenModel>()

  val screen = try {
    awaitUntil {
      logTesting { "Saw ${it.toSimpleString()}" }
      val matches =
        it.bottomSheetModel?.body is T &&
          (id == null || it.bottomSheetModel?.body?.eventTrackerScreenInfo?.eventTrackerScreenId == id) &&
          matching(it.bottomSheetModel?.body as T)
      if (!matches) previousModels += it
      matches
    }
  } catch (e: AssertionError) {
    val previousModelsMessage = "Previous models: ${previousModels.map { it.toSimpleString() }}"
    val message =
      if (id != null) {
        "Did not see expected Screen with SheetModel(${T::class.simpleName} id=$id). $previousModelsMessage"
      } else {
        "Did not see expected Screen with SheetModel(${T::class.simpleName}). $previousModelsMessage"
      }
    throw AssertionError(message, e)
  }

  screen.bottomSheetModel?.asClue { sheetModel ->
    assertSoftly {
      validate(sheetModel.body as T)
    }
  }
}
