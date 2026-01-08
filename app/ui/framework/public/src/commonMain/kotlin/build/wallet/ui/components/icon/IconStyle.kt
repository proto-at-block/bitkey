package build.wallet.ui.components.icon

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import build.wallet.statemachine.core.Icon
import build.wallet.ui.model.icon.IconImage
import build.wallet.ui.model.icon.IconImage.*
import build.wallet.ui.model.icon.IconSize
import build.wallet.ui.model.icon.IconTint
import build.wallet.ui.theme.WalletTheme

data class IconStyle(
  val color: Color,
)

/**
 * Returns actual size that should be used for an entire icon image, not the
 * actual icon within the image.
 */
val IconSize.dp: Dp
  get() = value.dp

@Composable
fun WalletTheme.iconStyle(
  icon: IconImage,
  color: Color,
  tint: IconTint?,
): IconStyle {
  return IconStyle(
    color =
      when (icon) {
        is LocalImage -> when {
          icon.icon.canApplyTint() -> applyTint(color, tint)
          else -> Color.Unspecified
        }

        is MarketIconImage ->
          if (!icon.icon.multiColor) {
            applyTint(color, tint)
          } else {
            Color.Unspecified
          }
        is UrlImage, IconImage.Loader -> color
        IconImage.LoadingBadge -> Color.Unspecified
      }
  )
}

@Composable
private fun WalletTheme.applyTint(
  color: Color,
  tint: IconTint?,
): Color {
  return when (tint) {
    IconTint.Primary -> colors.bitkeyPrimary
    IconTint.Foreground -> colors.foreground
    IconTint.Background -> colors.background
    IconTint.On60 -> colors.foreground60
    IconTint.On30 -> colors.foreground30
    IconTint.On10 -> colors.foreground10
    IconTint.Destructive -> colors.destructiveForeground
    IconTint.OutOfDate -> colors.outOfDate
    IconTint.OnTranslucent -> colors.translucentForeground
    IconTint.Green -> colors.positiveForeground
    IconTint.Warning -> colors.warningForeground
    IconTint.Success -> colors.calloutSuccessTrailingIconBackground
    IconTint.Information -> colors.calloutInformationTrailingIconBackground
    IconTint.White -> Color.White
    null -> when (color) {
      Color.Unspecified -> colors.primaryIcon
      else -> color
    }
  }
}

private fun Icon.canApplyTint(): Boolean {
  return when (this) {
    // Assets that don't support tint.
    Icon.LargeIconNetworkError,
    Icon.MediumIconTrustedContact,
    Icon.MoneyHomeHero,
    Icon.Bitcoin,
    Icon.BitkeyDeviceRaised,
    Icon.BitkeyDeviceRaisedSmall,
    Icon.BitkeyDevice3D,
    Icon.BitkeyFrontLit,
    Icon.SmallIconCheckboxSelected,
    Icon.SmallIconSettingsBadged,
    Icon.BitkeyLogo,
    Icon.BitcoinConsolidation,
    Icon.BitcoinBadged,
    Icon.LargeIconWarning,
    -> false

    else -> true
  }
}
