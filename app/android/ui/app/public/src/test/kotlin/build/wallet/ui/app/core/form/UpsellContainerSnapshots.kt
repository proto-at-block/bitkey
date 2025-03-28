package build.wallet.ui.app.core.form

import build.wallet.kotest.paparazzi.paparazziExtension
import build.wallet.statemachine.core.Icon
import build.wallet.statemachine.core.form.FormMainContentModel
import build.wallet.ui.model.StandardClick
import build.wallet.ui.model.button.ButtonModel
import build.wallet.ui.model.icon.IconBackgroundType
import build.wallet.ui.model.icon.IconModel
import build.wallet.ui.model.icon.IconSize
import build.wallet.ui.model.render
import io.kotest.core.spec.style.FunSpec

class UpsellContainerSnapshots : FunSpec({
  val paparazzi = paparazziExtension()

  test("upsell container") {
    paparazzi.snapshot {
      FormMainContentModel.Upsell(
        title = "Add a beneficiary",
        body = "Your investment is worth passing on. Add a beneficiary to ensure it stays in good hands.",
        iconModel = IconModel(
          icon = Icon.SmallIconInheritance,
          iconSize = IconSize.Large,
          iconBackgroundType = IconBackgroundType.Circle(
            IconSize.Avatar,
            IconBackgroundType.Circle.CircleColor.InheritanceSurface
          )
        ),
        primaryButton = ButtonModel(
          text = "Add",
          size = ButtonModel.Size.Regular,
          onClick = StandardClick {},
          leadingIcon = Icon.SmallIconPlus,
          treatment = ButtonModel.Treatment.Accent
        ),
        secondaryButton = ButtonModel(
          text = "Learn more",
          size = ButtonModel.Size.Regular,
          onClick = StandardClick {},
          treatment = ButtonModel.Treatment.Secondary
        )
      ).render()
    }
  }
})
