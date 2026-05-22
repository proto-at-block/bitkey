package build.wallet.ui.app.inheritance.claims.start

import androidx.compose.ui.Modifier
import build.wallet.kotest.paparazzi.paparazziExtension
import build.wallet.statemachine.inheritance.claims.start.StartClaimEducationBodyModel
import io.kotest.core.spec.style.FunSpec

class StartClaimEducationSnapshots : FunSpec({
  val paparazzi = paparazziExtension()

  test("start claim education") {
    paparazzi.snapshot {
      StartClaimEducationBodyModel(
        onBack = {},
        onContinue = {}
      ).render(modifier = Modifier)
    }
  }
})
