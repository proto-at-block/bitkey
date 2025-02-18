package build.wallet.ui.app.inheritance

import build.wallet.bitkey.relationships.EndorsedTrustedContactFake1
import build.wallet.kotest.paparazzi.paparazziExtension
import build.wallet.statemachine.inheritance.ManageInheritanceContactBodyModel
import build.wallet.statemachine.inheritance.ManageInheritanceContactBodyModel.ClaimControls
import build.wallet.ui.app.paparazzi.snapshotSheet
import io.kotest.core.spec.style.FunSpec

class ManageInheritanceContactSnapshots : FunSpec({
  val paparazzi = paparazziExtension()

  test("manage beneficiary contact sheet") {
    paparazzi.snapshotSheet(
      ManageInheritanceContactBodyModel(
        onClose = {},
        onRemove = {},
        onShare = {},
        recoveryEntity = EndorsedTrustedContactFake1,
        claimControls = ClaimControls.Cancel { }
      )
    )
  }
})
