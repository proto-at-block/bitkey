package build.wallet.ui.app.recovery

import build.wallet.kotest.paparazzi.paparazziExtension
import io.kotest.core.spec.style.FunSpec

class AppDelayNotifyInProgressScreenSnapshots : FunSpec({
  val paparazzi = paparazziExtension()

  test("app delay notifiy verification in progress screen") {
    paparazzi.snapshot {
      AppDelayNotifyInProgressPreview()
    }
  }
})
