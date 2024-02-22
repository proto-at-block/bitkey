package build.wallet.ui.components.data

import build.wallet.kotest.paparazzi.paparazziExtension
import build.wallet.ui.data.DataGroupWithLateTransactionPreview
import build.wallet.ui.data.DataRowGroupWithTotalPreview
import io.kotest.core.spec.style.FunSpec

class DataRowSnapshots : FunSpec({
  val paparazzi = paparazziExtension()

  test("data group with total") {
    paparazzi.snapshot {
      DataRowGroupWithTotalPreview()
    }
  }

  test("data group with late transaction") {
    paparazzi.snapshot {
      DataGroupWithLateTransactionPreview()
    }
  }
})
