package build.wallet.ui.app.recovery

import build.wallet.kotest.paparazzi.paparazziExtension
import build.wallet.statemachine.recovery.emergencyaccesskit.EmergencyAccessKitCodeNotRecognizedBodyModel
import build.wallet.statemachine.recovery.emergencyaccesskit.EmergencyAccessKitImportPasteMobileKeyBodyModel
import build.wallet.statemachine.recovery.emergencyaccesskit.EmergencyAccessKitImportWalletBodyModel
import build.wallet.statemachine.recovery.emergencyaccesskit.EmergencyAccessKitRestoreWalletBodyModel
import build.wallet.ui.app.core.form.FormScreen
import io.kotest.core.spec.style.FunSpec

class EmergencyAccessKitRecoveryScreenSnapshots : FunSpec({
  val paparazzi = paparazziExtension()

  test("emergency access import your wallet method selection screen") {
    paparazzi.snapshot {
      FormScreen(
        model = EmergencyAccessKitImportWalletBodyModel(
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
        model = EmergencyAccessKitImportPasteMobileKeyBodyModel(
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
        model = EmergencyAccessKitCodeNotRecognizedBodyModel(
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
        model = EmergencyAccessKitRestoreWalletBodyModel(
          onBack = {},
          onRestore = {}
        )
      )
    }
  }
})
