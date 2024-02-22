package build.wallet.ui.app.loading

import build.wallet.kotest.paparazzi.paparazziExtension
import io.kotest.core.spec.style.FunSpec

class SplashScreenSnapshots : FunSpec({
  val paparazzi = paparazziExtension()

  test("splash screen") {
    paparazzi.snapshot {
      PreviewSplashScreen()
    }
  }
})
