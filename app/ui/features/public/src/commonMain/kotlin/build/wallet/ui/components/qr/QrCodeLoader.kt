package build.wallet.ui.components.qr

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.min
import build.wallet.ui.components.loading.LoadingIndicator

private const val LOADING_INDICATOR_SIZE_RATIO = 0.25f // Loading indicator is 1/4 of QR code size

@Composable
fun QrCodeLoader(modifier: Modifier = Modifier) {
  BoxWithConstraints {
    // Use the most narrow constraint available.
    val qrCodeSizeDp = remember(constraints) {
      min(maxWidth, maxHeight)
    }

    Box(modifier = modifier.size(qrCodeSizeDp)) {
      // Show loading spinner while we are waiting for data
      LoadingIndicator(
        modifier =
          Modifier.size(qrCodeSizeDp * LOADING_INDICATOR_SIZE_RATIO)
            .align(Alignment.Center)
      )
    }
  }
}
