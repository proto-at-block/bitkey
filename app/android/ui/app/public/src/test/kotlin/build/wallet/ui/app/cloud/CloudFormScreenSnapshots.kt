package build.wallet.ui.app.cloud

import build.wallet.kotest.paparazzi.paparazziExtension
import build.wallet.platform.device.DevicePlatform
import build.wallet.statemachine.cloud.CloudSignInFailedScreenModel
import build.wallet.statemachine.cloud.SaveBackupInstructionsBodyModel
import build.wallet.ui.app.core.form.FormScreen
import io.kotest.core.spec.style.FunSpec

class CloudFormScreenSnapshots : FunSpec({
  val paparazzi = paparazziExtension()

  test("cloud sign in instructions screen") {
    paparazzi.snapshot {
      FormScreen(
        SaveBackupInstructionsBodyModel(
          onBackupClick = {},
          onLearnMoreClick = {},
          devicePlatform = DevicePlatform.Android,
          requiresHardware = false,
          isLoading = false
        )
      )
    }
  }

  test("cloud sign in failed screen") {
    paparazzi.snapshot {
      FormScreen(
        model =
          CloudSignInFailedScreenModel(
            onContactSupport = {},
            onBack = {},
            onTryAgain = {},
            devicePlatform = DevicePlatform.Android
          )
      )
    }
  }
})
