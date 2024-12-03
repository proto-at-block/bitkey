package build.wallet.ui.components.icon

import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import bitkey.shared.ui_core_public.generated.resources.Res
import build.wallet.ui.components.loading.LoadingIndicator
import build.wallet.ui.model.icon.IconImage
import build.wallet.ui.model.icon.IconSize
import build.wallet.ui.tokens.painter
import coil3.compose.AsyncImage
import coil3.compose.AsyncImagePainter
import coil3.compose.LocalPlatformContext
import coil3.compose.rememberAsyncImagePainter
import coil3.request.ImageRequest
import coil3.size.Dimension
import coil3.svg.SvgDecoder
import io.github.alexzhirkevich.compottie.Compottie
import io.github.alexzhirkevich.compottie.LottieCompositionSpec
import io.github.alexzhirkevich.compottie.rememberLottieComposition
import io.github.alexzhirkevich.compottie.rememberLottiePainter
import org.jetbrains.compose.resources.ExperimentalResourceApi
import androidx.compose.material3.Icon as MaterialIcon
import coil3.size.Size as CoilSize

@OptIn(ExperimentalResourceApi::class)
@Composable
fun UrlImage(
  modifier: Modifier = Modifier,
  image: IconImage.UrlImage,
  iconSize: IconSize,
  contentDescription: String?,
  imageAlpha: Float? = null,
) {
  val loadingAnimationComposition by rememberLottieComposition {
    LottieCompositionSpec.JsonString(
      Res.readBytes("files/loading.json").decodeToString()
    )
  }

  val painter = rememberLottiePainter(
    composition = loadingAnimationComposition,
    iterations = Compottie.IterateForever
  )

  UrlImage(
    modifier = modifier.size(iconSize.dp),
    imageUrl = image.url,
    imageSize = Size(iconSize.value.toFloat(), iconSize.value.toFloat()),
    imageAlpha = imageAlpha,
    imageContentDescription = contentDescription,
    fallbackPainter = image.fallbackIcon.painter(),
    placeholderPainter = painter
  )
}

@Composable
fun UrlImage(
  modifier: Modifier = Modifier,
  imageUrl: String,
  imageSize: Size = Size.Unspecified,
  imageTint: Color? = null,
  imageAlpha: Float? = null,
  imageContentDescription: String? = null,
  fallbackPainter: Painter,
  placeholderPainter: Painter,
) {
  val platformContext = LocalPlatformContext.current
  val imageRequest = remember(imageUrl, imageSize) {
    ImageRequest.Builder(platformContext)
      .decoderFactory(SvgDecoder.Factory())
      .data(imageUrl)
      .build()
  }

  AsyncImage(
    model = imageRequest,
    contentDescription = imageContentDescription,
    placeholder = placeholderPainter,
    modifier = modifier.size(imageSize.width.dp, imageSize.height.dp),
    contentScale = ContentScale.FillBounds,
    fallback = fallbackPainter,
    alpha = imageAlpha ?: 1f,
    colorFilter = imageTint?.let { ColorFilter.tint(color = imageTint) }
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
