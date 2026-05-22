package build.wallet.ui.app.receive

import androidx.compose.ui.Modifier
import build.wallet.kotest.paparazzi.paparazziExtension
import build.wallet.statemachine.receive.AddressVerificationPromptBodyModel
import io.kotest.core.spec.style.FunSpec

class AddressVerificationPromptScreenSnapshots : FunSpec({
  val paparazzi = paparazziExtension()

  test("address verification prompt screen") {
    paparazzi.snapshot {
      AddressVerificationPromptBodyModel(
        onBack = {},
        onVerify = {},
        onSkip = {}
      ).render(Modifier)
    }
  }

  test("address verification prompt screen - design system v2") {
    paparazzi.snapshot {
      AddressVerificationPromptBodyModel(
        onBack = {},
        onVerify = {},
        onSkip = {}
      ).render(Modifier)
    }
  }
})
