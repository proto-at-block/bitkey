package build.wallet.ui.components.icon

import androidx.compose.foundation.layout.size
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import build.wallet.ui.components.loading.LoadingIndicator
import build.wallet.ui.model.icon.IconImage
import build.wallet.ui.model.icon.IconSize
import build.wallet.ui.tokens.painter
import coil3.compose.AsyncImagePainter
import coil3.compose.LocalPlatformContext
import coil3.compose.rememberAsyncImagePainter
import coil3.request.ImageRequest
import coil3.size.Dimension
import coil3.svg.SvgDecoder
import androidx.compose.material3.Icon as MaterialIcon
import coil3.size.Size as CoilSize

@Composable
fun UrlImage(
  image: IconImage.UrlImage,
  iconSize: IconSize,
  contentDescription: String?,
  imageAlpha: Float? = null,
) {
  UrlImage(
    modifier = Modifier.size(iconSize.dp),
    imageUrl = image.url,
    imageSize = Size(iconSize.value.toFloat(), iconSize.value.toFloat()),
    imageAlpha = imageAlpha,
    imageContentDescription = contentDescription,
    loadingSize = iconSize,
    fallbackContent = {
      MaterialIcon(
        modifier = Modifier.size(iconSize.dp).alpha(imageAlpha ?: 1f),
        painter = image.fallbackIcon.painter(),
        contentDescription = "",
        tint = Color.Unspecified
      )
    }
  )
}

@Composable
fun UrlImage(
  modifier: Modifier = Modifier,
  imageUrl: String,
  imageSize: Size = Size.Unspecified,
  imageTint: Color = Color.Unspecified,
  imageAlpha: Float? = null,
  imageContentDescription: String? = null,
  loadingSize: IconSize,
  fallbackContent: @Composable () -> Unit,
) {
  val platformContext = LocalPlatformContext.current
  val imageRequest = remember(imageUrl, imageSize) {
    ImageRequest.Builder(platformContext)
      .decoderFactory(SvgDecoder.Factory())
      .data(imageUrl)
      .size(
        when (imageSize) {
          Size.Unspecified -> CoilSize.ORIGINAL
          else -> CoilSize(
            Dimension(imageSize.width.toInt()),
            Dimension(imageSize.height.toInt())
          )
        }
      )
      .build()
  }
  val asyncImagePainter =
    rememberAsyncImagePainter(
      model = imageRequest
    )

  val painterState by asyncImagePainter.state.collectAsState()

  when (painterState) {
    is AsyncImagePainter.State.Loading ->
      LoadingIndicator(modifier = Modifier.size(loadingSize.dp))

    is AsyncImagePainter.State.Success ->
      MaterialIcon(
        modifier = modifier.alpha(imageAlpha ?: 1f),
        painter = asyncImagePainter,
        contentDescription = imageContentDescription,
        tint = imageTint
      )

    is AsyncImagePainter.State.Empty,
    is AsyncImagePainter.State.Error,
    -> fallbackContent()
  }
}
