package build.wallet.ui.app.recovery

import build.wallet.bitkey.relationships.EndorsedTrustedContactFake1
import build.wallet.kotest.paparazzi.paparazziExtension
import build.wallet.statemachine.trustedcontact.view.ViewingTrustedContactSheetModel
import build.wallet.ui.app.paparazzi.snapshotSheet
import io.kotest.core.spec.style.FunSpec

class ViewingRecoveryContactSheetSnapshots : FunSpec({
  val paparazzi = paparazziExtension()

  test("viewing recovery contact sheet") {
    paparazzi.snapshotSheet(
      ViewingTrustedContactSheetModel(
        contact = EndorsedTrustedContactFake1,
        onRemove = {},
        onClosed = {}
      )
    )
  }
})
