package build.wallet.ui.app.inheritance

import androidx.compose.ui.Modifier
import build.wallet.kotest.paparazzi.paparazziExtension
import build.wallet.statemachine.trustedcontact.model.BeneficiaryOnboardingBodyModel
import io.kotest.core.spec.style.FunSpec

class BeneficiaryOnboardingSnapshots : FunSpec({
  val paparazzi = paparazziExtension()

  test("beneficiary onboarding") {
    paparazzi.snapshot {
      BeneficiaryOnboardingBodyModel(
        onBack = {},
        onContinue = {},
        onMoreInfo = {}
      ).render(modifier = Modifier)
    }
  }
})
