package build.wallet.ui.app.nfc

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import bitkey.account.HardwareType
import bitkey.ui.framework_public.generated.resources.Res
import build.wallet.ui.compose.getVideoResource
import build.wallet.ui.theme.Theme
import build.wallet.ui.tooling.LocalIsPreviewTheme
import org.jetbrains.compose.resources.painterResource as composePainterResource

@Composable
internal actual fun fwupUpdateHeroVideoResource(
  hardwareType: HardwareType,
  theme: Theme,
): String? {
  if (LocalIsPreviewTheme.current) return null
  return Res.getVideoResource(
    when (hardwareType) {
      HardwareType.W1 -> "pair"
      HardwareType.W3 -> return null
    }
  )
}

@Composable
internal actual fun FwupUpdateHeroPlatformImage(
  modifier: Modifier,
  theme: Theme,
  hardwareType: HardwareType,
  alpha: Float,
  contentScale: ContentScale,
) {
  val imageContent: @Composable (Modifier) -> Unit = if (hardwareType == HardwareType.W1) {
    { heroModifier ->
      FwupUpdateHeroPairImage(
        modifier = heroModifier,
        theme = theme,
        hardwareType = hardwareType,
        alpha = alpha,
        contentScale = contentScale
      )
    }
  } else {
    { heroModifier ->
      FwupUpdateHeroAndroidStillImage(
        modifier = heroModifier,
        theme = theme,
        alpha = alpha,
        contentScale = contentScale
      )
    }
  }

  imageContent(modifier)
}

@Composable
private fun FwupUpdateHeroPairImage(
  modifier: Modifier = Modifier,
  theme: Theme,
  hardwareType: HardwareType,
  alpha: Float,
  contentScale: ContentScale,
) {
  Image(
    painter = composePainterResource(updateFirmwareHeroImageResource(theme, hardwareType)),
    contentDescription = null,
    modifier = modifier,
    contentScale = contentScale,
    alignment = Alignment.Center,
    alpha = alpha
  )
}

@Composable
private fun FwupUpdateHeroAndroidStillImage(
  modifier: Modifier = Modifier,
  theme: Theme,
  alpha: Float,
  contentScale: ContentScale,
) {
  val context = LocalContext.current
  val imageBitmap = remember(theme) {
    context.assets.open(
      if (theme == Theme.DARK) {
        "fwup_update_android_dark.png"
      } else {
        "fwup_update_android_light.png"
      }
    ).use { input ->
      requireNotNull(BitmapFactory.decodeStream(input)) {
        "Failed to decode FWUP Android image for theme $theme"
      }.asImageBitmap()
    }
  }

  Image(
    bitmap = imageBitmap,
    contentDescription = null,
    modifier = modifier,
    contentScale = contentScale,
    alignment = Alignment.Center,
    alpha = alpha
  )
}
