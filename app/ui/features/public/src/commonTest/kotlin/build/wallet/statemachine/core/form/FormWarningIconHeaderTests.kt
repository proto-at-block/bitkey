package build.wallet.statemachine.core.form

import build.wallet.statemachine.core.Icon
import build.wallet.ui.model.icon.IconBackgroundType
import build.wallet.ui.model.icon.IconImage
import build.wallet.ui.model.icon.IconModel
import build.wallet.ui.model.icon.IconSize
import build.wallet.ui.model.icon.IconTint
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe

class FormWarningIconHeaderTests : FunSpec({
  test("replaces legacy large warning header icon with form warning treatment") {
    val iconModel =
      resolveLegacyHeaderWarningIconModel(
        iconModel = FormHeaderModel(icon = Icon.LargeIconWarningFilled, headline = "Legacy headline").iconModel
      ).shouldNotBeNull()

    iconModel.iconImage.shouldBe(IconImage.LocalImage(Icon.CriticalBadgeAlert))
    iconModel.iconSize.shouldBe(IconSize.Large)
    iconModel.iconTint.shouldBe(IconTint.Background)
    iconModel.iconBackgroundType.shouldBe(
      IconBackgroundType.Circle(
        circleSize = IconSize.Avatar,
        color = IconBackgroundType.Circle.CircleColor.InverseBackground
      )
    )
  }

  test("leaves non-warning legacy header icons unchanged") {
    val iconModel = IconModel(
      icon = Icon.Cloud,
      iconSize = IconSize.Avatar,
      iconTint = IconTint.Primary
    )

    resolveLegacyHeaderWarningIconModel(
      iconModel = iconModel
    ).shouldBe(iconModel)
  }
})
