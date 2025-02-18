package build.wallet.ui.app.moneyhome

import build.wallet.kotest.paparazzi.paparazziExtension
import build.wallet.statemachine.moneyhome.full.ViewingAddTrustedContactFormBodyModel
import build.wallet.ui.app.paparazzi.snapshotSheet
import io.kotest.core.spec.style.FunSpec

class ViewingAddTrustedContactSheetFormScreenSnapshots : FunSpec({
  val paparazzi = paparazziExtension()

  test("Add trusted contact sheet model screen") {
    paparazzi.snapshotSheet(
      ViewingAddTrustedContactFormBodyModel(
        onAddTrustedContact = {},
        onSkip = {},
        onClosed = {}
      )
    )
  }
})
