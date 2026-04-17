package build.wallet.ui.components.card

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import bitkey.account.HardwareType
import bitkey.ui.framework_public.generated.resources.Res
import build.wallet.statemachine.core.form.FormMainContentModel.DeviceStatusCard
import build.wallet.ui.compose.getVideoResource
import build.wallet.ui.theme.LocalTheme
import build.wallet.ui.theme.Theme
import build.wallet.ui.tokens.darkStyleDictionaryColors
import build.wallet.ui.tokens.lightStyleDictionaryColors

internal val BitkeyDeviceFallbackMediaSize = 200.dp

@Stable
internal class BitkeyDeviceMediaInteractionState {
  var isEnabled by mutableStateOf(false)
    internal set

  private var delegate: BitkeyDeviceMediaInteractionDelegate? = null

  internal fun setDelegate(delegate: BitkeyDeviceMediaInteractionDelegate?) {
    this.delegate = delegate
    isEnabled = delegate != null
  }

  fun beginInteraction(
    locationX: Float,
    locationY: Float,
    viewportWidth: Float,
    viewportHeight: Float,
  ) {
    delegate?.beginInteraction(
      locationX = locationX,
      locationY = locationY,
      viewportWidth = viewportWidth,
      viewportHeight = viewportHeight
    )
  }

  fun continueInteraction(
    locationX: Float,
    locationY: Float,
    viewportWidth: Float,
    viewportHeight: Float,
  ) {
    delegate?.continueInteraction(
      locationX = locationX,
      locationY = locationY,
      viewportWidth = viewportWidth,
      viewportHeight = viewportHeight
    )
  }

  fun endInteraction(
    locationX: Float,
    locationY: Float,
    velocityX: Float,
    velocityY: Float,
    viewportWidth: Float,
    viewportHeight: Float,
  ) {
    delegate?.endInteraction(
      locationX = locationX,
      locationY = locationY,
      velocityX = velocityX,
      velocityY = velocityY,
      viewportWidth = viewportWidth,
      viewportHeight = viewportHeight
    )
  }

  fun stopMomentum() {
    delegate?.stopMomentum()
  }
}

@Composable
internal fun rememberBitkeyDeviceMediaInteractionState(): BitkeyDeviceMediaInteractionState =
  remember { BitkeyDeviceMediaInteractionState() }

internal interface BitkeyDeviceMediaInteractionDelegate {
  fun beginInteraction(
    locationX: Float,
    locationY: Float,
    viewportWidth: Float,
    viewportHeight: Float,
  )

  fun continueInteraction(
    locationX: Float,
    locationY: Float,
    viewportWidth: Float,
    viewportHeight: Float,
  )

  fun endInteraction(
    locationX: Float,
    locationY: Float,
    velocityX: Float,
    velocityY: Float,
    viewportWidth: Float,
    viewportHeight: Float,
  )

  fun stopMomentum()
}

@Composable
internal expect fun BitkeyDeviceMedia(
  modifier: Modifier = Modifier,
  content: DeviceStatusCard.VideoContent,
  serialNumber: String? = null,
  batteryPercentage: Int? = null,
  hardwareType: HardwareType = HardwareType.W3,
  interactionState: BitkeyDeviceMediaInteractionState,
  shouldPlayWaitingIntro: Boolean = false,
)

@Composable
internal expect fun BitkeyDeviceMediaInteractionOverlay(
  modifier: Modifier = Modifier,
  interactionState: BitkeyDeviceMediaInteractionState,
)

internal expect fun supportsBitkeyDevice3DMedia(hardwareType: HardwareType): Boolean

@Composable
internal expect fun supportsInteractiveBitkeyWaitingMedia(hardwareType: HardwareType): Boolean

@Composable
internal fun legacyBitkeyDeviceCardBackgroundColor(): Color =
  when (LocalTheme.current) {
    Theme.LIGHT -> lightStyleDictionaryColors.subtleBackground
    Theme.DARK -> darkStyleDictionaryColors.subtleBackground
  }

@Composable
internal fun bitkeyDeviceVideoResource(content: DeviceStatusCard.VideoContent): String =
  when (content) {
    DeviceStatusCard.VideoContent.BITKEY_ROTATE ->
      when (LocalTheme.current) {
        Theme.LIGHT -> Res.getVideoResource("bitkey_rotate")
        Theme.DARK -> Res.getVideoResource("bitkey_rotate_dark")
      }
    DeviceStatusCard.VideoContent.BITKEY_WAITING_3D -> Res.getVideoResource("bitkey_waiting_3d")
  }
