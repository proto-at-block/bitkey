package build.wallet.ui.app.core

import build.wallet.kotest.paparazzi.paparazziExtension
import build.wallet.statemachine.core.LoadingSuccessBodyModel
import build.wallet.statemachine.core.LoadingSuccessBodyModel.State.Success
import io.kotest.core.spec.style.FunSpec

class LoadingSuccessScreenSnapshots : FunSpec({
  val paparazzi = paparazziExtension()

  test("loading state") {
    paparazzi.snapshot {
      LoadingSuccessScreen(
        model =
          LoadingSuccessBodyModel(
            state = LoadingSuccessBodyModel.State.Loading,
            id = null
          )
      )
    }
  }

  test("success state") {
    paparazzi.snapshot {
      LoadingSuccessScreen(
        model =
          LoadingSuccessBodyModel(
            message = "You succeeded",
            state = Success,
            id = null
          )
      )
    }
  }
})
