package build.wallet.ui.app.loading

import build.wallet.kotest.paparazzi.paparazziExtension
import build.wallet.statemachine.core.SplashBodyModel
import io.kotest.core.spec.style.FunSpec
import kotlin.time.Duration.Companion.seconds

class SplashScreenSnapshots : FunSpec({
  val paparazzi = paparazziExtension()

  test("splash screen") {
    paparazzi.snapshot {
      SplashScreen(
        model =
          SplashBodyModel(
            bitkeyWordMarkAnimationDelay = 0.seconds,
            bitkeyWordMarkAnimationDuration = 0.seconds
          )
      )
    }
  }
})
