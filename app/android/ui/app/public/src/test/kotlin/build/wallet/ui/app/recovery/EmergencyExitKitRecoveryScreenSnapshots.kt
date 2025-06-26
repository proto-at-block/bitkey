package build.wallet.ui.app.recovery

import build.wallet.kotest.paparazzi.paparazziExtension
import build.wallet.statemachine.recovery.emergencyexitkit.EmergencyExitKitCodeNotRecognizedBodyModel
import build.wallet.statemachine.recovery.emergencyexitkit.EmergencyExitKitImportPasteAppKeyBodyModel
import build.wallet.statemachine.recovery.emergencyexitkit.EmergencyExitKitImportWalletBodyModel
import build.wallet.statemachine.recovery.emergencyexitkit.EmergencyExitKitRestoreWalletBodyModel
import build.wallet.ui.app.core.form.FormScreen
import io.kotest.core.spec.style.FunSpec

class EmergencyExitKitRecoveryScreenSnapshots : FunSpec({
  val paparazzi = paparazziExtension()

  test("Emergency Exit Kit import your wallet method selection screen") {
    paparazzi.snapshot {
      FormScreen(
        model = EmergencyExitKitImportWalletBodyModel(
          onBack = {},
          onScanQRCode = {},
          onEnterManually = {}
        )
      )
    }
  }

  test("Emergency Exit Kit paste your App Key screen") {
    paparazzi.snapshot {
      FormScreen(
        model = EmergencyExitKitImportPasteAppKeyBodyModel(
          enteredText = "",
          onBack = {},
          onEnterTextChanged = {},
          onPasteButtonClick = {},
          onContinue = {}
        )
      )
    }
  }

  test("Emergency Exit Kit code not recognized error screen") {
    paparazzi.snapshot {
      FormScreen(
        model = EmergencyExitKitCodeNotRecognizedBodyModel(
          arrivedFromManualEntry = false,
          onBack = {},
          onScanQRCode = {},
          onImport = {}
        )
      )
    }
  }

  test("Emergency Exit Kit restore your wallet screen") {
    paparazzi.snapshot {
      FormScreen(
        model = EmergencyExitKitRestoreWalletBodyModel(
          onBack = {},
          onRestore = {}
        )
      )
    }
  }
})
