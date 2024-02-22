package build.wallet.ui.app.recovery

import build.wallet.kotest.paparazzi.paparazziExtension
import build.wallet.statemachine.core.Icon
import build.wallet.statemachine.core.ScreenPresentationStyle
import build.wallet.statemachine.core.form.FormBodyModel
import build.wallet.statemachine.recovery.hardware.initiating.NewDeviceReadyQuestionModel
import build.wallet.ui.app.core.form.FormScreen
import build.wallet.ui.model.Click
import build.wallet.ui.model.button.ButtonModel
import build.wallet.ui.model.icon.IconBackgroundType
import build.wallet.ui.model.icon.IconModel
import build.wallet.ui.model.icon.IconSize
import io.kotest.core.spec.style.FunSpec

class NewDeviceReadyQuestionFormScreenSnapshots : FunSpec({
  val paparazzi = paparazziExtension()

  test("new device ready question screen") {
    paparazzi.snapshot {
      FormScreen(
        model =
          NewDeviceReadyQuestionModel(
            showingNoDeviceAlert = false,
            onNoDeviceAlertDismiss = {},
            onBack = {},
            primaryAction =
              ButtonModel(
                text = "Yes",
                onClick = Click.StandardClick { },
                size = ButtonModel.Size.Footer
              ),
            secondaryAction =
              ButtonModel(
                text = "No",
                onClick = Click.StandardClick { },
                treatment = ButtonModel.Treatment.Secondary,
                size = ButtonModel.Size.Footer
              ),
            showBack = true,
            backIconModel =
              IconModel(
                icon = Icon.SmallIconArrowLeft,
                iconSize = IconSize.Accessory,
                iconBackgroundType = IconBackgroundType.Circle(circleSize = IconSize.Regular)
              ),
            presentationStyle = ScreenPresentationStyle.Modal
          ).body as FormBodyModel
      )
    }
  }
})
