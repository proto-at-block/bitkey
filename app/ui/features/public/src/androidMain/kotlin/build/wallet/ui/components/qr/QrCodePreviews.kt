package build.wallet.ui.components.qr

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import build.wallet.ui.tooling.PreviewWalletTheme

@Preview
@Composable
internal fun QrCodePreview() {
  PreviewWalletTheme {
    Box(modifier = Modifier.size(300.dp)) {
      QrCode(data = "https://www.youtube.com/watch?v=dQw4w9WgXcQ")
    }
  }
}
