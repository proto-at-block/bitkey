package build.wallet.ui.app.mobilepay

import build.wallet.kotest.paparazzi.paparazziExtension
import io.kotest.core.spec.style.FunSpec

class MobilePayScreenSnapshots : FunSpec({
  val paparazzi = paparazziExtension()

  test("mobile pay screen - mobile pay disabled") {
    paparazzi.snapshot {
      MobilePayStatusScreenDisabledPreview()
    }
  }

  test("mobile pay screen - mobile pay enabled, confirmation disabled") {
    paparazzi.snapshot {
      MobilePayStatusScreenEnabledPreview()
    }
  }

  test("mobile pay screen with revamp - mobile pay enabled, confirmation enabled") {
    paparazzi.snapshot {
      MobilePayStatusScreenEnabledWithDialogPreviewAndRevamp()
    }
  }
})
