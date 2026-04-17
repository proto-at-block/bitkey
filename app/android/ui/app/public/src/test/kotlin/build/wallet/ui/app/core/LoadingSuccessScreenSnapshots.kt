package build.wallet.ui.app.core

import build.wallet.kotest.paparazzi.paparazziExtension
import build.wallet.statemachine.core.LoadingSuccessBodyModel
import build.wallet.statemachine.core.LoadingSuccessBodyModel.State.Success
import build.wallet.ui.model.StandardClick
import build.wallet.ui.model.button.ButtonModel
import io.kotest.core.spec.style.FunSpec

class LoadingSuccessScreenSnapshots : FunSpec({
  val paparazzi = paparazziExtension(maxPercentDifference = 0.3)

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

  test("loading state - with buttons") {
    paparazzi.snapshot {
      LoadingSuccessScreen(
        model =
          LoadingSuccessBodyModel(
            state = LoadingSuccessBodyModel.State.Loading,
            id = null,
            primaryButton = ButtonModel(
              text = "Primary",
              onClick = StandardClick {},
              size = ButtonModel.Size.Footer
            ),
            secondaryButton = ButtonModel(
              text = "Secondary",
              onClick = StandardClick {},
              treatment = ButtonModel.Treatment.Secondary,
              size = ButtonModel.Size.Footer
            )
          )
      )
    }
  }

  test("success state") {
    paparazzi.snapshot {
      LoadingSuccessScreen(
        model =
          LoadingSuccessBodyModel(
            message = "Success",
            state = Success,
            id = null
        )
      )
    }
  }

  test("loading state with design system v2") {
    paparazzi.snapshot(designSystemUpdatesEnabled = true) {
      LoadingSuccessScreen(
        model =
          LoadingSuccessBodyModel(
            state = LoadingSuccessBodyModel.State.Loading,
            id = null
          )
      )
    }
  }

  test("success state with design system v2") {
    paparazzi.snapshot(designSystemUpdatesEnabled = true) {
      LoadingSuccessScreen(
        model =
          LoadingSuccessBodyModel(
            message = "Success",
            state = Success,
            id = null
          )
      )
    }
  }
})
