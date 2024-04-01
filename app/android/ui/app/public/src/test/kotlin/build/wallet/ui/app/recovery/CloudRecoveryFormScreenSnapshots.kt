package build.wallet.ui.app.recovery

import build.wallet.kotest.paparazzi.paparazziExtension
import build.wallet.platform.device.DevicePlatform.Android
import build.wallet.statemachine.recovery.cloud.CloudBackupFoundModel
import build.wallet.statemachine.recovery.cloud.CloudBackupNotFoundBodyModel
import build.wallet.statemachine.recovery.cloud.CloudNotSignedInBodyModel
import build.wallet.statemachine.recovery.cloud.SocialRecoveryExplanationModel
import build.wallet.ui.app.core.form.FormScreen
import io.kotest.core.spec.style.FunSpec

class CloudRecoveryFormScreenSnapshots : FunSpec({
  val paparazzi = paparazziExtension()

  test("cloud backup found screen") {
    paparazzi.snapshot {
      FormScreen(
        model =
          CloudBackupFoundModel(
            devicePlatform = Android,
            onBack = {},
            onRestore = {},
            onLostBitkeyClick = {},
            showSocRecButton = true
          )
      )
    }
  }

  test("cloud backup not found screen") {
    paparazzi.snapshot {
      FormScreen(
        model =
          CloudBackupNotFoundBodyModel(
            onBack = {},
            onCheckCloudAgain = {},
            onCannotAccessCloud = {},
            onImportEmergencyAccessKit = {},
            onShowTroubleshootingSteps = {}
          )
      )
    }
  }

  test("cloud not signed in screen") {
    paparazzi.snapshot {
      FormScreen(
        model =
          CloudNotSignedInBodyModel(
            onBack = {},
            onCheckCloudAgain = {},
            onCannotAccessCloud = {},
            onImportEmergencyAccessKit = {},
            onShowTroubleshootingSteps = {}
          )
      )
    }
  }

  test("social recovery explaination screen") {
    paparazzi.snapshot {
      FormScreen(
        model =
          SocialRecoveryExplanationModel(
            onBack = {},
            onContinue = {}
          )
      )
    }
  }
})
