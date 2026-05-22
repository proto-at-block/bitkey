package build.wallet.statemachine.core.form

import build.wallet.statemachine.core.Icon
import build.wallet.ui.model.icon.IconBackgroundType
import build.wallet.ui.model.icon.IconImage
import build.wallet.ui.model.icon.IconModel
import build.wallet.ui.model.icon.IconSize
import build.wallet.ui.model.icon.IconTint
import build.wallet.ui.tokens.market.MarketIcons
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe

class DesignSystemV2WarningIconHeaderTests : FunSpec({
  test("replaces legacy large warning header icon with dsv2 warning treatment") {
    val iconModel =
      resolveLegacyHeaderWarningIconModelForDesignSystemV2(
        iconModel = FormHeaderModel(icon = Icon.LargeIconWarningFilled, headline = "Legacy headline").iconModel
      ).shouldNotBeNull()

    iconModel.iconImage.shouldBe(IconImage.MarketIconImage(MarketIcons.CriticalBadgeAlert))
    iconModel.iconSize.shouldBe(IconSize.Large)
    iconModel.iconTint.shouldBe(IconTint.Background)
    iconModel.iconBackgroundType.shouldBe(
      IconBackgroundType.Circle(
        circleSize = IconSize.Avatar,
        color = IconBackgroundType.Circle.CircleColor.InverseBackground
      )
    )
  }

  test("leaves non-warning legacy header icons unchanged when dsv2 is enabled") {
    val iconModel = IconModel(
      icon = Icon.SmallIconCloud,
      iconSize = IconSize.Avatar,
      iconTint = IconTint.Primary
    )

    resolveLegacyHeaderWarningIconModelForDesignSystemV2(
      iconModel = iconModel
    ).shouldBe(iconModel)
  }
})
