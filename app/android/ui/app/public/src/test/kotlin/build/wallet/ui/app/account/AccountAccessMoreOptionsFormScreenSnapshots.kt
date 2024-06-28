package build.wallet.ui.app.account

import build.wallet.kotest.paparazzi.paparazziExtension
import build.wallet.statemachine.account.AccountAccessMoreOptionsFormBodyModel
import build.wallet.statemachine.account.EmergencyAccountAccessMoreOptionsFormBodyModel
import build.wallet.ui.app.core.form.FormScreen
import io.kotest.core.spec.style.FunSpec

class AccountAccessMoreOptionsFormScreenSnapshots :
  FunSpec({
    val paparazzi = paparazziExtension()

    test("regular app variant - create or recover wallet screen") {
      paparazzi.snapshot {
        FormScreen(
          AccountAccessMoreOptionsFormBodyModel(
            onBack = {},
            onBeTrustedContactClick = {},
            onRestoreYourWalletClick = {},
            onResetExistingDevice = {}
          )
        )
      }
    }

    test("EAK app variant - emergency recovery screen") {
      paparazzi.snapshot {
        FormScreen(
          EmergencyAccountAccessMoreOptionsFormBodyModel(
            onBack = {},
            onRestoreEmergencyAccessKit = {}
          )
        )
      }
    }
  })
