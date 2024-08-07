package build.wallet.ui.components.icon

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import build.wallet.ui.model.icon.IconImage
import build.wallet.ui.model.icon.IconSize

// TODO: Add common implementation to support iOS and JVM
@Composable
expect fun UrlImage(
  image: IconImage.UrlImage,
  iconSize: IconSize,
  contentDescription: String?,
  imageAlpha: Float? = null,
)

@Composable
expect fun UrlImage(
  modifier: Modifier = Modifier,
  imageUrl: String,
  imageSize: Size = Size.Unspecified,
  imageTint: Color = Color.Unspecified,
  imageAlpha: Float? = null,
  imageContentDescription: String? = null,
  loadingSize: IconSize,
  fallbackContent: @Composable () -> Unit,
)
