package build.wallet.ui.components.loading

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import build.wallet.ui.theme.WalletTheme

enum class FormLoaderStyle {
  Legacy,
  DotLoading,
}

@Composable
fun FormLoader(
  color: Color = WalletTheme.colors.foreground,
  style: FormLoaderStyle = FormLoaderStyle.Legacy,
) {
  Column(
    modifier = Modifier.fillMaxSize(),
    horizontalAlignment = Alignment.CenterHorizontally,
    verticalArrangement = Arrangement.Center
  ) {
    when (style) {
      FormLoaderStyle.Legacy -> {
        LoadingIndicator(
          modifier = Modifier.size(64.dp),
          color = color
        )
      }

      FormLoaderStyle.DotLoading -> {
        DesignSystemDotLoadingIndicator(modifier = Modifier.size(80.dp))
      }
    }
  }
}
