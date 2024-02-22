package build.wallet.ui.app.recovery

import build.wallet.kotest.paparazzi.paparazziExtension
import build.wallet.statemachine.recovery.emergencyaccesskit.EmergencyAccessKitCodeNotRecognized
import build.wallet.statemachine.recovery.emergencyaccesskit.EmergencyAccessKitImportPasteMobileKeyModel
import build.wallet.statemachine.recovery.emergencyaccesskit.EmergencyAccessKitImportWalletModel
import build.wallet.statemachine.recovery.emergencyaccesskit.EmergencyAccessKitRestoreWallet
import build.wallet.ui.app.core.form.FormScreen
import io.kotest.core.spec.style.FunSpec

class EmergencyAccessKitRecoveryScreenSnapshots : FunSpec({
  val paparazzi = paparazziExtension()

  test("emergency access import your wallet method selection screen") {
    paparazzi.snapshot {
      FormScreen(
        model =
          EmergencyAccessKitImportWalletModel(
            onBack = {},
            onScanQRCode = {},
            onEnterManually = {}
          )
      )
    }
  }

  test("emergency access paste your mobile key screen") {
    paparazzi.snapshot {
      FormScreen(
        model =
          EmergencyAccessKitImportPasteMobileKeyModel(
            enteredText = "",
            onBack = {},
            onEnterTextChanged = {},
            onPasteButtonClick = {},
            onContinue = {}
          )
      )
    }
  }

  test("emergency access code not recognized error screen") {
    paparazzi.snapshot {
      FormScreen(
        model =
          EmergencyAccessKitCodeNotRecognized(
            arrivedFromManualEntry = false,
            onBack = {},
            onScanQRCode = {},
            onImport = {}
          )
      )
    }
  }

  test("emergency access restore your wallet screen") {
    paparazzi.snapshot {
      FormScreen(
        model =
          EmergencyAccessKitRestoreWallet(
            onBack = {},
            onRestore = {}
          )
      )
    }
  }
})
