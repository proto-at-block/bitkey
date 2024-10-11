package build.wallet.ui.app.export

import build.wallet.kotest.paparazzi.paparazziExtension
import build.wallet.statemachine.core.form.FormBodyModel
import build.wallet.statemachine.export.ExportToolsSelectionModel
import build.wallet.statemachine.export.view.exportTransactionHistoryLoadingSheetModel
import build.wallet.statemachine.export.view.exportTransactionHistorySheetModel
import build.wallet.statemachine.export.view.exportWalletDescriptorLoadingSheetModel
import build.wallet.statemachine.export.view.exportWalletDescriptorSheetModel
import build.wallet.ui.app.core.form.FormScreen
import io.kotest.core.spec.style.FunSpec

class ExportToolsSelectionSnapshots : FunSpec({
  val paparazzi = paparazziExtension()

  test("export selection screen") {
    paparazzi.snapshot {
      FormScreen(
        ExportToolsSelectionModel(
          onBack = {},
          onExportDescriptorClick = {},
          onExportTransactionHistoryClick = {}
        )
      )
    }
  }

  test("export selection screen with descriptor export – loading") {
    paparazzi.snapshot {
      FormScreen(exportWalletDescriptorLoadingSheetModel(onClosed = {}).body as FormBodyModel)
    }
  }

  test("export selection screen with descriptor export") {
    paparazzi.snapshot {
      FormScreen(exportWalletDescriptorSheetModel(onClick = {}, onClosed = {}).body as FormBodyModel)
    }
  }

  test("export selection screen with transaction history export") {
    paparazzi.snapshot {
      FormScreen(exportTransactionHistoryLoadingSheetModel(onClosed = {}).body as FormBodyModel)
    }
  }

  test("export selection screen with transaction history export – loading") {
    paparazzi.snapshot {
      FormScreen(
        exportTransactionHistorySheetModel(onCtaClicked = {
        }, onClosed = {}).body as FormBodyModel
      )
    }
  }
})
