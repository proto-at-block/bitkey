package build.wallet.ui.components.card

import build.wallet.kotest.paparazzi.paparazziExtension
import io.kotest.core.spec.style.FunSpec

class CardSnapshots : FunSpec({
  val paparazzi = paparazziExtension()

  test("sample card container with text") {
    paparazzi.snapshot {
      PreviewCard()
    }
  }

  test("inheritance pending claim card - beneficiary") {
    paparazzi.snapshot {
      PendingClaimCardPreview()
    }
  }
})
