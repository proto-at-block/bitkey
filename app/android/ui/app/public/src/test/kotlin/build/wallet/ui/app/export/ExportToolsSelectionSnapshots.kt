package build.wallet.ui.app.export

import build.wallet.kotest.paparazzi.paparazziExtension
import build.wallet.statemachine.export.exportToolsSelectionModel
import build.wallet.ui.app.core.form.FormScreen
import io.kotest.core.spec.style.FunSpec

class ExportToolsSelectionSnapshots : FunSpec({
  val paparazzi = paparazziExtension()

  test("export selection screen") {
    paparazzi.snapshot {
      FormScreen(
        exportToolsSelectionModel(
          onBack = {},
          onExportDescriptorClick = {},
          onExportTransactionHistoryClick = {}
        )
      )
    }
  }
})
