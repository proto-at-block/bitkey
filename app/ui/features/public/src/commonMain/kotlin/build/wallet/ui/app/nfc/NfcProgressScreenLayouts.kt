package build.wallet.ui.app.nfc

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import bitkey.ui.framework_public.generated.resources.Res
import bitkey.ui.framework_public.generated.resources.ios_nfc_tap
import build.wallet.ui.components.button.Button
import build.wallet.ui.model.StandardClick
import build.wallet.ui.model.button.ButtonModel.Size.Footer
import build.wallet.ui.model.button.ButtonModel.Treatment.Translucent
import build.wallet.ui.system.BackHandler
import build.wallet.ui.theme.WalletTheme
import org.jetbrains.compose.resources.painterResource

/**
 * Generic iOS layout for NFC progress screens with gradient background.
 *
 * @param modifier Modifier for the root container
 * @param statusContent Content to display below the NFC icon (typically status labels)
 */
@Composable
fun NfcProgressScreenIosLayout(
  modifier: Modifier = Modifier,
  statusContent: @Composable ColumnScope.() -> Unit,
) {
  val nfcBlue = WalletTheme.colors.nfcBlue.copy(alpha = 0.6f)
  Column(
    modifier = modifier
      .fillMaxSize()
      .background(
        Brush.verticalGradient(
          colors = listOf(Color.Black, WalletTheme.colors.nfcBlue)
        )
      ),
    horizontalAlignment = Alignment.CenterHorizontally
  ) {
    Spacer(modifier = Modifier.height(48.dp))
    Box {
      Spacer(
        modifier = Modifier
          .matchParentSize()
          .drawBehind {
            drawRect(
              brush = Brush.verticalGradient(
                colors = listOf(Color.Transparent, nfcBlue, Color.Transparent),
                startY = size.height,
                endY = 0f
              )
            )
          }
          .blur(28.dp, BlurredEdgeTreatment.Rectangle)
      )
      Column(
        modifier =
          Modifier
            .fillMaxWidth()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp)
      ) {
        Image(
          alignment = Alignment.Center,
          painter = painterResource(Res.drawable.ios_nfc_tap),
          contentDescription = ""
        )
        statusContent()
      }
    }
  }
}

/**
 * Generic Android layout for NFC progress screens with blur background and status indicator.
 *
 * @param modifier Modifier for the root container
 * @param onCancel Optional callback for cancel button (null hides button)
 * @param statusIndicator Status indicator composable (typically NfcProgressStatusIndicator)
 * @param statusLabel Status label composable (typically NfcStatusLabel)
 */
@Composable
fun NfcProgressScreenAndroidLayout(
  modifier: Modifier = Modifier,
  onCancel: (() -> Unit)?,
  statusIndicator: @Composable () -> Unit,
  statusLabel: @Composable () -> Unit,
) {
  BackHandler {
    onCancel?.invoke()
  }

  NfcBlurBackground {
    Column(
      modifier =
        modifier
          .background(WalletTheme.colors.foreground.copy(alpha = 0.1F))
          .padding(horizontal = 20.dp)
          .navigationBarsPadding()
          .fillMaxSize(),
      horizontalAlignment = Alignment.CenterHorizontally
    ) {
      Spacer(Modifier.weight(1F))

      statusIndicator()

      Box(modifier = Modifier.weight(1F)) {
        Box(modifier = Modifier.align(Alignment.TopCenter)) {
          statusLabel()
        }
      }

      Button(
        text = "Cancel",
        modifier =
          Modifier.alpha(
            when (onCancel) {
              null -> 0f
              else -> 1f
            }
          ),
        treatment = Translucent,
        size = Footer,
        onClick = StandardClick {
          onCancel?.invoke()
        }
      )
      Spacer(modifier = Modifier.height(24.dp))
    }
  }
}
