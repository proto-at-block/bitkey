package build.wallet.ui.app.transactions

import build.wallet.kotest.paparazzi.paparazziExtension
import build.wallet.ui.app.moneyhome.TransactionListPreview
import io.kotest.core.spec.style.FunSpec

class TransactionListSnapshots : FunSpec({
  val paparazzi = paparazziExtension()

  test("transaction list") {
    paparazzi.snapshot {
      TransactionListPreview()
    }
  }
})
