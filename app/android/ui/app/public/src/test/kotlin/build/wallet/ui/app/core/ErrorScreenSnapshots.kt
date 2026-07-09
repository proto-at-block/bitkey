package build.wallet.ui.app.core

import build.wallet.kotest.paparazzi.paparazziExtension
import build.wallet.statemachine.core.AppSegment
import build.wallet.statemachine.core.ButtonDataModel
import build.wallet.statemachine.core.ErrorData
import build.wallet.statemachine.core.ErrorFormBodyModel
import build.wallet.statemachine.core.SheetModel
import build.wallet.statemachine.core.form.RenderContext
import build.wallet.ui.app.core.form.FormScreen
import build.wallet.ui.app.paparazzi.snapshotSheet
import build.wallet.ui.model.toolbar.ToolbarAccessoryModel.IconAccessory
import build.wallet.ui.model.toolbar.ToolbarModel
import io.kotest.core.spec.style.FunSpec

class ErrorScreenSnapshots : FunSpec({
  val paparazzi = paparazziExtension()

  test("error with subline screen") {
    paparazzi.snapshot {
      FormScreen(errorWithSublineModel)
    }
  }

  test("error without subline screen") {
    paparazzi.snapshot {
      FormScreen(errorWithoutSublineModel)
    }
  }

  test("error with back screen") {
    paparazzi.snapshot {
      FormScreen(errorWithBack)
    }
  }

  test("error sheet") {
    paparazzi.snapshotSheet(
      SheetModel(
        body = errorForSheet,
        onClosed = {}
      )
    )
  }
})

private object TestAppSegment : AppSegment {
  override val id: String = "test"
}

private val testErrorData = ErrorData(
  segment = TestAppSegment,
  actionDescription = "Test error",
  cause = Exception("Test error")
)

private val errorWithSublineModel =
  ErrorFormBodyModel(
    title = "Error message",
    subline = "Error description",
    primaryButton = ButtonDataModel(text = "Done", onClick = {}),
    secondaryButton = ButtonDataModel(text = "Go Back", onClick = {}),
    eventTrackerScreenId = null,
    errorData = testErrorData
  )

private val errorForSheet = ErrorFormBodyModel(
  title = "Error message",
  subline = "Error description",
  primaryButton = ButtonDataModel(text = "Done", onClick = {}),
  secondaryButton = ButtonDataModel(text = "Go Back", onClick = {}),
  eventTrackerScreenId = null,
  renderContext = RenderContext.Sheet,
  errorData = testErrorData
)

private val errorWithoutSublineModel =
  ErrorFormBodyModel(
    title = "Error message",
    primaryButton = ButtonDataModel(text = "Done", onClick = {}),
    eventTrackerScreenId = null,
    errorData = testErrorData
  )

private val errorWithBack =
  ErrorFormBodyModel(
    title = "Error message",
    primaryButton = ButtonDataModel(text = "Done", onClick = {}),
    toolbar =
      ToolbarModel(
        leadingAccessory =
          IconAccessory.BackAccessory(
            onClick = { }
          )
      ),
    eventTrackerScreenId = null,
    errorData = testErrorData
  )
