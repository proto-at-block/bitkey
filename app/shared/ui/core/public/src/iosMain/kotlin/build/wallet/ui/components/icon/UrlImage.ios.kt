package build.wallet.ui.components.icon

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import build.wallet.ui.model.icon.IconImage
import build.wallet.ui.model.icon.IconSize

@Composable
actual fun UrlImage(
  image: IconImage.UrlImage,
  iconSize: IconSize,
  contentDescription: String?,
  imageAlpha: Float?,
) {
  // NOTE: Not implemented, see expect function
}

@Composable
actual fun UrlImage(
  modifier: Modifier,
  imageUrl: String,
  imageSize: Size,
  imageTint: Color,
  imageAlpha: Float?,
  imageContentDescription: String?,
  loadingSize: IconSize,
  fallbackContent: @Composable () -> Unit,
) {
  // NOTE: Not implemented, see expect function
}
