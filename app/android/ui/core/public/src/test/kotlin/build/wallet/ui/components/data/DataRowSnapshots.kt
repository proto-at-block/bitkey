package build.wallet.ui.components.data

import build.wallet.kotest.paparazzi.paparazziExtension
import build.wallet.ui.data.DataGroupWithLateTransaction
import build.wallet.ui.data.DataRowGroupWithTotal
import io.kotest.core.spec.style.FunSpec

class DataRowSnapshots : FunSpec({
  val paparazzi = paparazziExtension()

  test("data group with total") {
    paparazzi.snapshot {
      DataRowGroupWithTotal()
    }
  }

  test("data group with late transaction") {
    paparazzi.snapshot {
      DataGroupWithLateTransaction()
    }
  }
})
