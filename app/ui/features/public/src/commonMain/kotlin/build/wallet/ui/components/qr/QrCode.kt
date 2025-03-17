package build.wallet.ui.components.qr

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import build.wallet.ui.components.qr.CellShape.Square

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
