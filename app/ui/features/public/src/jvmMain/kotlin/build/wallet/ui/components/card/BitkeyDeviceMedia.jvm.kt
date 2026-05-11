package build.wallet.ui.components.card

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import bitkey.account.HardwareType
import build.wallet.statemachine.core.form.FormMainContentModel.DeviceStatusCard
import build.wallet.ui.components.video.VideoPlayer

@Composable
internal actual fun BitkeyDeviceMedia(
  modifier: Modifier,
  content: DeviceStatusCard.VideoContent,
  serialNumber: String?,
  batteryPercentage: Int?,
  hardwareType: HardwareType,
  interactionState: BitkeyDeviceMediaInteractionState,
) {
  Box(
    modifier = modifier,
    contentAlignment = Alignment.Center
  ) {
    VideoPlayer(
      modifier = Modifier.size(BitkeyDeviceFallbackMediaSize),
      resourcePath = bitkeyDeviceVideoResource(content),
      backgroundColor = legacyBitkeyDeviceCardBackgroundColor(),
      isLooping = true,
      autoStart = true
    )
  }
}

@Composable
internal actual fun BitkeyDeviceMediaInteractionOverlay(
  modifier: Modifier,
  interactionState: BitkeyDeviceMediaInteractionState,
) = Unit

internal actual fun supportsBitkeyDevice3DMedia(hardwareType: HardwareType): Boolean = false
