package build.wallet.statemachine.send.hardwareconfirmation

import androidx.compose.foundation.background
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import bitkey.ui.framework_public.generated.resources.Res
import bitkey.ui.framework_public.generated.resources.coil_placement_dark_poster
import bitkey.ui.framework_public.generated.resources.coil_placement_light_poster
import build.wallet.platform.device.DevicePlatform
import build.wallet.ui.components.explainer.Statement as ExplainerStatement
import build.wallet.ui.components.label.LabelTreatment
import build.wallet.ui.components.video.VideoPlayer
import build.wallet.ui.components.video.VideoScalingMode
import build.wallet.ui.compose.getVideoResource
import build.wallet.ui.model.ComposeModel
import build.wallet.ui.theme.LocalTheme
import build.wallet.ui.theme.Theme
import build.wallet.ui.theme.WalletTheme
import build.wallet.ui.tokens.LabelType
import build.wallet.ui.tooling.LocalIsPreviewTheme
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.painterResource

data class HardwareConfirmationHelpContentModel(
  private val content: HardwareConfirmationHelpContent,
  private val devicePlatform: DevicePlatform,
) : ComposeModel {
  @Composable
  override fun render(modifier: Modifier) {
    val statements = content.statements(devicePlatform)
    val theme = LocalTheme.current
    val videoResourcePath = content
      .videoResourceName(devicePlatform, theme)

    Column(
      modifier = modifier,
      verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
      if (videoResourcePath != null) {
        Box(
          modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            .clip(RoundedCornerShape(12.dp))
            .background(WalletTheme.colors.foreground10)
        ) {
          TapBitkeyPlacementVideo(
            modifier = Modifier.fillMaxSize(),
            backgroundColor = WalletTheme.colors.foreground10,
            videoResourceName = videoResourcePath,
            scalingMode = VideoScalingMode.FIT,
            topCornerRadius = 12.dp
          )
        }
      }

      Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(16.dp)
      ) {
        statements.forEachIndexed { index, statement ->
          Column(verticalArrangement = Arrangement.spacedBy(0.dp)) {
            ExplainerStatement(
              title = statement.title,
              body = statement.body,
              icon = null,
              leadingText = "[${index + 1}]",
              leadingTextType = LabelType.Body2MonoCaps,
              leadingTextTreatment = LabelTreatment.Primary,
              titleType = LabelType.Body2MonoCaps,
              titleTreatment = LabelTreatment.Primary,
              bodyType = LabelType.Body3Regular,
              bodyTreatment = LabelTreatment.Secondary
            )
          }
        }
      }
    }
  }
}

@Composable
internal fun TapBitkeyPlacementVideo(
  modifier: Modifier = Modifier,
  backgroundColor: Color,
  videoResourceName: String,
  scalingMode: VideoScalingMode,
  topCornerRadius: Dp = 0.dp,
) {
  if (LocalIsPreviewTheme.current) {
    Image(
      modifier = modifier,
      painter = painterResource(tapBitkeyPlacementPosterResource(LocalTheme.current)),
      contentDescription = null,
      contentScale = ContentScale.Crop
    )
  } else {
    VideoPlayer(
      modifier = modifier,
      resourcePath = Res.getVideoResource(videoResourceName),
      isLooping = true,
      backgroundColor = backgroundColor,
      scalingMode = scalingMode,
      topCornerRadius = topCornerRadius
    )
  }
}

private fun tapBitkeyPlacementPosterResource(theme: Theme): DrawableResource =
  when (theme) {
    Theme.DARK -> Res.drawable.coil_placement_dark_poster
    Theme.LIGHT -> Res.drawable.coil_placement_light_poster
  }
