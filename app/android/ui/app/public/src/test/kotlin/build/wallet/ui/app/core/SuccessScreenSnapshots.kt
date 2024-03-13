package build.wallet.ui.app.core

import build.wallet.kotest.paparazzi.paparazziExtension
import build.wallet.statemachine.core.ButtonDataModel
import build.wallet.statemachine.core.SuccessBodyModel
import build.wallet.ui.app.core.form.FormScreen
import io.kotest.core.spec.style.FunSpec

class SuccessScreenSnapshots : FunSpec({
  val paparazzi = paparazziExtension()

  test("success with subline screen") {
    paparazzi.snapshot {
      FormScreen(
        model = SuccessBodyModel(
          title = "You have succeeded",
          message = "Congratulations for doing such a great job.",
          primaryButtonModel = ButtonDataModel("Done", onClick = {}),
          id = null
        )
      )
    }
  }

  test("success without subline screen") {
    paparazzi.snapshot {
      FormScreen(
        model = SuccessBodyModel(
          title = "You have succeeded",
          primaryButtonModel = ButtonDataModel("Done", onClick = {}),
          id = null
        )
      )
    }
  }

  test("success implicit") {
    paparazzi.snapshot {
      FormScreen(
        model = SuccessBodyModel(
          title = "You have succeeded",
          primaryButtonModel = null,
          id = null
        )
      )
    }
  }
})
