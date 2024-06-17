package build.wallet.ui.app.lock

import build.wallet.kotest.paparazzi.paparazziExtension
import build.wallet.statemachine.core.SplashLockModel
import build.wallet.ui.model.StandardClick
import build.wallet.ui.model.button.ButtonModel
import io.kotest.core.spec.style.FunSpec

class FwupNfcSnapshots : FunSpec({
  val paparazzi = paparazziExtension()

  test("Splash Lock Screen") {
    paparazzi.snapshot {
      SplashLockScreen(
        model = SplashLockModel(
          unlockButtonModel = ButtonModel(
            text = "Unlock",
            treatment = ButtonModel.Treatment.Translucent,
            size = ButtonModel.Size.Footer,
            onClick = StandardClick {}
          ),
          eventTrackerScreenInfo = null,
          key = ""
        )
      )
    }
  }
})
