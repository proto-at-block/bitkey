package build.wallet.ui.app.status

import build.wallet.kotest.paparazzi.paparazziExtension
import build.wallet.platform.device.DevicePlatform
import build.wallet.statemachine.core.AgeRestrictedBodyModel
import build.wallet.ui.app.core.form.FormScreen
import io.kotest.core.spec.style.FunSpec

class AgeRestrictedScreenSnapshots : FunSpec({

  val paparazzi = paparazziExtension()

  test("age restricted screen android") {
    paparazzi.snapshot {
      FormScreen(
        model = AgeRestrictedBodyModel(
          devicePlatform = DevicePlatform.Android
        )
      )
    }
  }

  test("age restricted screen ios") {
    paparazzi.snapshot {
      FormScreen(
        model = AgeRestrictedBodyModel(
          devicePlatform = DevicePlatform.IOS
        )
      )
    }
  }
})
