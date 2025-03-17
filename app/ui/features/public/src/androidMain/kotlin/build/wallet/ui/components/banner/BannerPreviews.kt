package build.wallet.ui.components.banner

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import build.wallet.statemachine.core.Icon
import build.wallet.ui.tooling.PreviewWalletTheme

@Preview
@Composable
fun AllBannersPreview() {
  PreviewWalletTheme {
    Column(
      modifier = Modifier.padding(24.dp),
      horizontalAlignment = Alignment.CenterHorizontally,
      verticalArrangement = Arrangement.spacedBy(24.dp)
    ) {
      val showLeadingIconValues = listOf(false, true)
      showLeadingIconValues.forEach { showLeadingIcon ->
        BannerSize.entries.forEach { size ->
          BannerTreatment.entries.forEach { treatment ->
            Banner(
              text = "${size.name} ${treatment.name}",
              leadingIcon = Icon.SmallIconBitkey.takeIf { showLeadingIcon },
              treatment = treatment,
              size = size
            )
          }
        }
      }
    }
  }
}
