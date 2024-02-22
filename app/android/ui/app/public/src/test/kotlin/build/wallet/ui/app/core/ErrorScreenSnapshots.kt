package build.wallet.ui.app.core

import build.wallet.kotest.paparazzi.paparazziExtension
import build.wallet.statemachine.core.ButtonDataModel
import build.wallet.statemachine.core.ErrorFormBodyModel
import build.wallet.ui.app.core.form.FormScreen
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
})

private val errorWithSublineModel =
  ErrorFormBodyModel(
    title = "Error message",
    subline = "Error description",
    primaryButton = ButtonDataModel(text = "Done", onClick = {}),
    secondaryButton = ButtonDataModel(text = "Go Back", onClick = {}),
    eventTrackerScreenId = null
  )

private val errorWithoutSublineModel =
  ErrorFormBodyModel(
    title = "Error message",
    primaryButton = ButtonDataModel(text = "Done", onClick = {}),
    eventTrackerScreenId = null
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
    eventTrackerScreenId = null
  )
