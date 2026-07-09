package build.wallet.ui.components.card

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import build.wallet.statemachine.core.form.FormMainContentModel.DeviceStatusCard
import build.wallet.ui.components.callout.Callout
import build.wallet.ui.components.icon.IconImage
import build.wallet.ui.theme.WalletTheme
import kotlinx.coroutines.delay

@Composable
fun BitkeyDevice(
  model: DeviceStatusCard,
  modifier: Modifier = Modifier,
) {
  var mediaAlpha by remember { mutableStateOf(0.0f) }
  val mediaInteractionState = rememberBitkeyDeviceMediaInteractionState()
  val bitkeyDeviceCardCornerRadius = 12.dp
  val supports3DMedia = supportsBitkeyDevice3DMedia(model.hardwareType)
  val useFallbackVideoSurfaceTreatment =
    !supports3DMedia &&
      model.deviceVideo != null
  val fallbackVideoSurfaceBackgroundColor = legacyBitkeyDeviceCardBackgroundColor()
  val mediaContainerModifier =
    remember {
      Modifier
        .fillMaxWidth()
        .height(280.dp)
    }

  LaunchedEffect(Unit) {
    delay(300)
    mediaAlpha = 1.0f
  }

  Card(
    modifier = modifier.clip(RoundedCornerShape(bitkeyDeviceCardCornerRadius)),
    backgroundColor = when {
      useFallbackVideoSurfaceTreatment -> fallbackVideoSurfaceBackgroundColor
      else -> WalletTheme.colors.secondary
    },
    paddingValues = PaddingValues(0.dp),
    cornerRadius = bitkeyDeviceCardCornerRadius
  ) {
    Column(
      horizontalAlignment = Alignment.CenterHorizontally,
      verticalArrangement = Arrangement.spacedBy(16.dp),
      modifier = Modifier.padding(12.dp)
    ) {
      when {
        model.deviceImage != null -> {
          IconImage(model = model.deviceImage)
        }
        model.deviceVideo != null -> {
          Box(
            modifier = mediaContainerModifier,
            contentAlignment = Alignment.Center
          ) {
            BitkeyDeviceMedia(
              modifier = Modifier
                .matchParentSize()
                .alpha(mediaAlpha),
              content = model.deviceVideo,
              serialNumber = model.deviceSerialNumber,
              batteryPercentage = model.deviceBatteryPercentage,
              hardwareType = model.hardwareType,
              interactionState = mediaInteractionState
            )

            if (mediaInteractionState.isEnabled) {
              BitkeyDeviceMediaInteractionOverlay(
                modifier = Modifier
                  .matchParentSize()
                  .zIndex(1.0f),
                interactionState = mediaInteractionState
              )
            }
          }
        }
      }

      Callout(model = model.statusCallout)
    }
  }
}
