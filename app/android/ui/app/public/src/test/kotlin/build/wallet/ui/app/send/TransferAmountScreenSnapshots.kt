package build.wallet.ui.app.send

import build.wallet.kotest.paparazzi.paparazziExtension
import io.kotest.core.spec.style.FunSpec

class TransferAmountScreenSnapshots : FunSpec({
  val paparazzi = paparazziExtension()

  test("transfer amount entry screen - no entry") {
    paparazzi.snapshot {
      TransferAmountScreenNoEntryPreview()
    }
  }

  test("transfer amount entry screen - with entry") {
    paparazzi.snapshot {
      TransferAmountScreenWithEntryPreview()
    }
  }

  test("transfer amount entry screen - with banner") {
    paparazzi.snapshot {
      TransferAmountScreenWithBannerPreview()
    }
  }
})
