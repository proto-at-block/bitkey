package build.wallet.ui.components.qr

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import build.wallet.ui.components.qr.CellShape.Square
import build.wallet.ui.tooling.PreviewWalletTheme
import org.jetbrains.compose.ui.tooling.preview.Preview

/**
 * @param [data] - content that will be encoded in the QR code. If `null`, loading spinner is shown.
 * @param [cellShape] - shape of the QR code cells
 */
@Composable
expect fun QrCode(
  modifier: Modifier = Modifier,
  data: String?,
  cellShape: CellShape = Square,
)

/**
 * Enum describing how the QR code cell should be rendered
 */
enum class CellShape {
  Square,
  Circle,
  RoundedSquare,
}

@Preview
@Composable
internal fun QrCodePreview() {
  PreviewWalletTheme {
    Box(modifier = Modifier.size(300.dp)) {
      QrCode(data = "https://www.youtube.com/watch?v=dQw4w9WgXcQ")
    }
  }
}
