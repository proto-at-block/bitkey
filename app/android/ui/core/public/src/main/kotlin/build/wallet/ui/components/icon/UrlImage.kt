package build.wallet.ui.components.icon

import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import build.wallet.ui.components.loading.LoadingIndicator
import build.wallet.ui.model.icon.IconImage
import build.wallet.ui.model.icon.IconSize
import build.wallet.ui.tokens.painter
import coil.compose.AsyncImagePainter
import coil.compose.rememberAsyncImagePainter
import coil.decode.SvgDecoder
import coil.request.ImageRequest
import coil.size.Size
import androidx.compose.material3.Icon as MaterialIcon

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
    imageSize = Size(iconSize.value, iconSize.value),
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
  imageSize: Size = Size.ORIGINAL,
  imageTint: Color = Color.Unspecified,
  imageAlpha: Float? = null,
  imageContentDescription: String? = null,
  loadingSize: IconSize,
  fallbackContent: @Composable () -> Unit,
) {
  val asyncImagePainter =
    rememberAsyncImagePainter(
      model =
        ImageRequest.Builder(LocalContext.current)
          .decoderFactory(SvgDecoder.Factory())
          .data(imageUrl)
          .size(imageSize)
          .build()
    )

  when (asyncImagePainter.state) {
    is AsyncImagePainter.State.Loading ->
      LoadingIndicator(modifier = Modifier.size(loadingSize.dp))

    is AsyncImagePainter.State.Success ->
      MaterialIcon(
        modifier = modifier.alpha(imageAlpha ?: 1f),
        painter = asyncImagePainter,
        contentDescription = imageContentDescription,
        tint = imageTint
      )

    is AsyncImagePainter.State.Empty, is AsyncImagePainter.State.Error ->
      fallbackContent()
  }
}
