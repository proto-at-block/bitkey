package build.wallet.ui.app.nfc

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import build.wallet.ui.system.isBlurSupported
import build.wallet.ui.theme.WalletTheme

@Composable
fun NfcBlurBackground(content: @Composable () -> Unit) {
  Box(
    modifier =
      Modifier
        .fillMaxSize()
        .background(WalletTheme.colors.foreground)
  ) {
    // Only add the background blurs in versions that support [blur]
    if (isBlurSupported()) {
      Box(
        modifier =
          Modifier
            .size(465.dp)
            .blur(radius = 150.dp, edgeTreatment = BlurredEdgeTreatment.Unbounded)
            .offset(x = 99.dp, y = (-25).dp)
            .background(
              color = Color(0xff192516),
              shape = RoundedCornerShape(size = 465.dp)
            )
      )

      Box(
        modifier =
          Modifier
            .size(465.dp)
            .blur(radius = 150.dp, edgeTreatment = BlurredEdgeTreatment.Unbounded)
            .offset(x = (-233).dp, y = 14.dp)
            .background(
              color = Color(0xff122641),
              shape = RoundedCornerShape(size = 465.dp)
            )
      )
    }

    content()
  }
}
