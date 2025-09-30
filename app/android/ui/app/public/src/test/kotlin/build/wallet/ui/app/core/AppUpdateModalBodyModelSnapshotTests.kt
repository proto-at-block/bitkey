package build.wallet.ui.app.core

import build.wallet.kotest.paparazzi.paparazziExtension
import build.wallet.statemachine.core.AppUpdateModalBodyModel
import build.wallet.ui.app.core.form.FormScreen
import io.kotest.core.spec.style.FunSpec

class AppUpdateModalBodyModelSnapshotTests : FunSpec({
  val paparazzi = paparazziExtension()

  test("app update modal") {
    paparazzi.snapshot {
      FormScreen(
        model = AppUpdateModalBodyModel(
          onUpdate = {},
          onCancel = {}
        )
      )
    }
  }
})
