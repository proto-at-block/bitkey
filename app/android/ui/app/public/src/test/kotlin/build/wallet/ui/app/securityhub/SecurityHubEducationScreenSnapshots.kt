package build.wallet.ui.app.securityhub

import build.wallet.kotest.paparazzi.paparazziExtension
import io.kotest.core.spec.style.FunSpec

class SecurityHubEducationScreenSnapshots : FunSpec({
  val paparazzi = paparazziExtension()

  test("security hub education emergency exit kit") {
    paparazzi.snapshot {
      EmergencyExitKitSecurityHubEducationPreview()
    }
  }

  test("security hub education multiple fingerprints") {
    paparazzi.snapshot {
      MultipleFingerprintsSecurityHubEducationPreview()
    }
  }

  test("security hub education recovery contacts") {
    paparazzi.snapshot {
      RecoveryContactsSecurityHubEducationPreview()
    }
  }

  test("security hub education critical alerts") {
    paparazzi.snapshot {
      CriticalAlertsSecurityHubEducationPreview()
    }
  }

  test("security hub education transaction verification") {
    paparazzi.snapshot {
      TransactionVerificationSecurityHubEducationPreview()
    }
  }
})
