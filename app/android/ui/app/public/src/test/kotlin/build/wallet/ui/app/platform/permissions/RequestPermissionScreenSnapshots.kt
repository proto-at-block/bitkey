package build.wallet.ui.app.platform.permissions

import build.wallet.kotest.paparazzi.paparazziExtension
import io.kotest.core.spec.style.FunSpec

class RequestPermissionScreenSnapshots : FunSpec({
  val paparazzi = paparazziExtension()

  test("PreviewRequestPermissionScreen") {
    paparazzi.snapshot {
      PreviewRequestPermissionScreen()
    }
  }
})
