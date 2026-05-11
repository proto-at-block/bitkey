package build.wallet.ui.app.securityhub

import build.wallet.kotest.paparazzi.paparazziExtension
import io.kotest.core.spec.style.FunSpec

class SecurityHubEducationScreenSnapshots : FunSpec({
  val paparazzi = paparazziExtension()

  test("security hub education emergency exit kit") {
    paparazzi.snapshot {
      EmergencyExitKitSecurityHubEducationDesignSystemV2Preview()
    }
  }

  test("security hub education multiple fingerprints") {
    paparazzi.snapshot {
      MultipleFingerprintsSecurityHubEducationDesignSystemV2Preview()
    }
  }

  test("security hub education recovery contacts") {
    paparazzi.snapshot {
      RecoveryContactsSecurityHubEducationDesignSystemV2Preview()
    }
  }

  test("security hub education critical alerts") {
    paparazzi.snapshot {
      CriticalAlertsSecurityHubEducationDesignSystemV2Preview()
    }
  }

  test("security hub education transaction verification") {
    paparazzi.snapshot {
      TransactionVerificationSecurityHubEducationDesignSystemV2Preview()
    }
  }
})
