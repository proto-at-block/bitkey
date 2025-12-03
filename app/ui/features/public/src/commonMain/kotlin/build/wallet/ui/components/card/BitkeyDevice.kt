package build.wallet.ui.components.card

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.unit.dp
import bitkey.ui.framework_public.generated.resources.Res
import build.wallet.statemachine.core.form.FormMainContentModel.DeviceStatusCard
import build.wallet.ui.components.callout.Callout
import build.wallet.ui.components.icon.IconImage
import build.wallet.ui.components.video.VideoPlayer
import build.wallet.ui.compose.getVideoResource
import build.wallet.ui.theme.LocalTheme
import build.wallet.ui.theme.Theme
import build.wallet.ui.theme.WalletTheme
import kotlinx.coroutines.delay

@Composable
fun BitkeyDevice(
  model: DeviceStatusCard,
  modifier: Modifier = Modifier,
) {
  // Remove the showVideo state variable - no longer needed
  // var showVideo by remember { mutableStateOf(true) }

  // Simplified alpha management - just fade in on first load
  var videoAlpha by remember { mutableStateOf(0.0f) }

  LaunchedEffect(Unit) {
    delay(300)
    videoAlpha = 1.0f
  }

  Card(
    modifier = modifier,
    backgroundColor = WalletTheme.colors.subtleBackground,
    paddingValues = PaddingValues(0.dp),
    cornerRadius = 24.dp
  ) {
    Column(
      horizontalAlignment = Alignment.CenterHorizontally,
      verticalArrangement = Arrangement.spacedBy(16.dp),
      modifier = Modifier.padding(12.dp)
    ) {
      when {
        model.deviceImage != null -> {
          IconImage(
            model = model.deviceImage
          )
        }
        model.deviceVideo != null -> {
          Box(
            modifier = Modifier.size(250.dp),
            contentAlignment = Alignment.Center
          ) {
            VideoPlayer(
              modifier = Modifier
                .size(200.dp)
                .alpha(videoAlpha),
              resourcePath = when (model.deviceVideo) {
                DeviceStatusCard.VideoContent.BITKEY_ROTATE -> {
                  when (LocalTheme.current) {
                    Theme.LIGHT -> Res.getVideoResource("bitkey_rotate")
                    Theme.DARK -> Res.getVideoResource("bitkey_rotate_dark")
                  }
                }
              },
              isLooping = true,
              autoStart = true
            )
          }
        }
      }
      Callout(model = model.statusCallout)
    }
  }
}
