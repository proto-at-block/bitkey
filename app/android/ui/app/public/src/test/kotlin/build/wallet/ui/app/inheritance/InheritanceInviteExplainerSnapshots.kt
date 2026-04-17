package build.wallet.ui.app.inheritance

import androidx.compose.ui.Modifier
import build.wallet.kotest.paparazzi.paparazziExtension
import build.wallet.statemachine.recovery.socrec.add.InheritanceInviteExplainerBodyModel
import io.kotest.core.spec.style.FunSpec

class InheritanceInviteExplainerSnapshots : FunSpec({
  val paparazzi = paparazziExtension()

  test("inheritance invite explainer") {
    paparazzi.snapshot(designSystemUpdatesEnabled = true) {
      InheritanceInviteExplainerBodyModel(
        onBack = {},
        onContinue = {},
        learnMore = {}
      ).render(modifier = Modifier)
    }
  }
})
