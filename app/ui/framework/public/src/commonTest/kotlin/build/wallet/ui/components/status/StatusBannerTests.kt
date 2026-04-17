package build.wallet.ui.components.status

import build.wallet.ui.model.status.BannerStyle
import build.wallet.ui.theme.Theme
import build.wallet.ui.tokens.darkStyleDictionaryColors
import build.wallet.ui.tokens.lightStyleDictionaryColors
import build.wallet.ui.tokens.lightStyleDictionaryColorsDesignSystemUpdates
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

class StatusBannerTests : FunSpec({
  test("uses legacy warning colors in light mode when DSV2 is disabled") {
    statusBannerColors(
      style = BannerStyle.Warning,
      theme = Theme.LIGHT,
      isDesignSystemV2Enabled = false
    ).shouldBe(
      StatusBannerColors(
        backgroundColor = lightStyleDictionaryColors.warning,
        contentColor = lightStyleDictionaryColors.warningForeground
      )
    )
  }

  test("uses legacy destructive colors in light mode when DSV2 is disabled") {
    statusBannerColors(
      style = BannerStyle.Destructive,
      theme = Theme.LIGHT,
      isDesignSystemV2Enabled = false
    ).shouldBe(
      StatusBannerColors(
        backgroundColor = lightStyleDictionaryColors.destructiveForeground.copy(alpha = 0.1f),
        contentColor = lightStyleDictionaryColors.destructiveForeground
      )
    )
  }

  test("uses black background and dark warning accent in light mode when DSV2 is enabled") {
    statusBannerColors(
      style = BannerStyle.Warning,
      theme = Theme.LIGHT,
      isDesignSystemV2Enabled = true
    ).shouldBe(
      StatusBannerColors(
        backgroundColor = lightStyleDictionaryColorsDesignSystemUpdates.inverseBackground,
        contentColor = darkStyleDictionaryColors.warningForeground
      )
    )
  }

  test("uses black background and dark destructive accent in light mode when DSV2 is enabled") {
    statusBannerColors(
      style = BannerStyle.Destructive,
      theme = Theme.LIGHT,
      isDesignSystemV2Enabled = true
    ).shouldBe(
      StatusBannerColors(
        backgroundColor = lightStyleDictionaryColorsDesignSystemUpdates.inverseBackground,
        contentColor = darkStyleDictionaryColors.destructiveForeground
      )
    )
  }
})
