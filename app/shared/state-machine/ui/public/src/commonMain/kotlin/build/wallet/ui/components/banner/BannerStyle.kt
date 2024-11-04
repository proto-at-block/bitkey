package build.wallet.ui.components.banner

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.graphics.Color
import build.wallet.ui.components.banner.BannerTreatment.Default
import build.wallet.ui.components.banner.BannerTreatment.Ready
import build.wallet.ui.components.banner.BannerTreatment.Warning
import build.wallet.ui.theme.WalletTheme

internal data class BannerStyle(
  val contentColor: Color,
  val backgroundColor: Color,
)

@Composable
@ReadOnlyComposable
internal fun WalletTheme.bannerStyle(treatment: BannerTreatment): BannerStyle {
  return BannerStyle(
    contentColor =
      when (treatment) {
        Default -> colors.foreground
        Warning -> colors.warningForeground
        Ready -> colors.bitkeyPrimary
      },
    backgroundColor =
      when (treatment) {
        Default -> colors.foreground10
        Warning -> colors.warning
        Ready -> colors.positive
      }
  )
}
