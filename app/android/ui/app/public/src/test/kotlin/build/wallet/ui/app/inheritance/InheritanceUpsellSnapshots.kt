package build.wallet.ui.app.inheritance

import build.wallet.kotest.paparazzi.paparazziExtension
import build.wallet.statemachine.inheritance.InheritanceUpsellSheetModel
import build.wallet.ui.app.paparazzi.snapshotSheet
import io.kotest.core.spec.style.FunSpec

class InheritanceUpsellSnapshots : FunSpec({
  val paparazzi = paparazziExtension()

  test("inheritance upsell sheet") {
    paparazzi.snapshotSheet(InheritanceUpsellSheetModel({}, {}))
  }
})
