package build.wallet.ui.app.inheritance

import androidx.compose.ui.Modifier
import build.wallet.kotest.paparazzi.paparazziExtension
import build.wallet.statemachine.recovery.socrec.add.InheritanceInviteSetupBodyModel
import io.kotest.core.spec.style.FunSpec

class InheritanceInviteSetupSnapshots : FunSpec({
  val paparazzi = paparazziExtension()

  test("inheritance invite setup") {
    paparazzi.snapshot {
      InheritanceInviteSetupBodyModel(
        onBack = {},
        onContinue = {},
        learnMore = {}
      ).render(modifier = Modifier)
    }
  }
})
