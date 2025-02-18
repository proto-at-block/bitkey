package build.wallet.ui.app.export

import build.wallet.kotest.paparazzi.paparazziExtension
import build.wallet.statemachine.export.ExportToolsSelectionModel
import build.wallet.statemachine.export.view.exportTransactionHistoryLoadingSheetModel
import build.wallet.statemachine.export.view.exportTransactionHistorySheetModel
import build.wallet.statemachine.export.view.exportWalletDescriptorLoadingSheetModel
import build.wallet.statemachine.export.view.exportWalletDescriptorSheetModel
import build.wallet.ui.app.core.form.FormScreen
import build.wallet.ui.app.paparazzi.snapshotSheet
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
    paparazzi.snapshotSheet(
      exportWalletDescriptorLoadingSheetModel(onClosed = {})
    )
  }

  test("export selection screen with descriptor export") {
    paparazzi.snapshotSheet(
      exportWalletDescriptorSheetModel(onClick = {}, onClosed = {})
    )
  }

  test("export selection screen with transaction history export") {
    paparazzi.snapshotSheet(
      exportTransactionHistoryLoadingSheetModel(onClosed = {})
    )
  }

  test("export selection screen with transaction history export – loading") {
    paparazzi.snapshotSheet(
      exportTransactionHistorySheetModel(
        onCtaClicked = {},
        onClosed = {}
      )
    )
  }
})
