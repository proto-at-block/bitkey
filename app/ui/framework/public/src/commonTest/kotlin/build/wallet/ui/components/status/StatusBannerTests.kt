package build.wallet.ui.components.status

import build.wallet.ui.model.status.BannerStyle
import build.wallet.ui.theme.Theme
import build.wallet.ui.tokens.darkStyleDictionaryColors
import build.wallet.ui.tokens.lightStyleDictionaryColorsDesignSystemUpdates
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

class StatusBannerTests : FunSpec({
  test("uses black background and dark warning accent in light mode") {
    statusBannerColors(
      style = BannerStyle.Warning,
      theme = Theme.LIGHT
    ).shouldBe(
      StatusBannerColors(
        backgroundColor = lightStyleDictionaryColorsDesignSystemUpdates.inverseBackground,
        contentColor = darkStyleDictionaryColors.warningForeground
      )
    )
  }

  test("uses black background and dark destructive accent in light mode") {
    statusBannerColors(
      style = BannerStyle.Destructive,
      theme = Theme.LIGHT
    ).shouldBe(
      StatusBannerColors(
        backgroundColor = lightStyleDictionaryColorsDesignSystemUpdates.inverseBackground,
        contentColor = darkStyleDictionaryColors.destructiveForeground
      )
    )
  }
})
