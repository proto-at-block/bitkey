package build.wallet.ui.components.banner

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

@Composable
fun WipBanner(
  modifier: Modifier = Modifier,
  message: String = "Work in progress!",
) {
  Banner(
    modifier = modifier,
    text = "\uD83D\uDEA7 $message \uD83D\uDEA7",
    treatment = BannerTreatment.Warning,
    size = BannerSize.Compact
  )
}
