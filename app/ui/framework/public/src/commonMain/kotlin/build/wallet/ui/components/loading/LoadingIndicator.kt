package build.wallet.ui.components.loading

import androidx.compose.foundation.Image
import androidx.compose.material.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.layout.ContentScale
import bitkey.ui.framework_public.generated.resources.Res
import bitkey.ui.framework_public.generated.resources.loader_static
import bitkey.ui.framework_public.generated.resources.loading_badge_static
import build.wallet.ui.theme.WalletTheme
import build.wallet.ui.tooling.LocalIsPreviewTheme
import io.github.alexzhirkevich.compottie.*
import io.github.alexzhirkevich.compottie.dynamic.rememberLottieDynamicProperties
import org.jetbrains.compose.resources.ExperimentalResourceApi
import org.jetbrains.compose.resources.painterResource

@Composable
fun LoadingIndicator(
  modifier: Modifier = Modifier,
  color: Color = WalletTheme.colors.foreground,
) {
  if (LocalIsPreviewTheme.current) {
    // NOTE: Display static loader image for preview/snapshot tests
    Icon(
      modifier = modifier,
      painter = painterResource(Res.drawable.loader_static),
      contentDescription = null,
      tint = color
    )
  } else {
    val painter = LoadingIndicatorPainter(
      color = color
    )
    Image(
      painter = painter,
      modifier = modifier,
      contentScale = ContentScale.FillBounds,
      contentDescription = null
    )
  }
}

@OptIn(ExperimentalResourceApi::class, ExperimentalCompottieApi::class)
@Composable
fun LoadingIndicatorPainter(color: Color = WalletTheme.colors.foreground): Painter {
  if (LocalIsPreviewTheme.current) {
    // NOTE: Display static loader image for preview/snapshot tests
    return painterResource(Res.drawable.loader_static)
  }

  val loadingAnimationComposition by rememberLottieComposition(color) {
    LottieCompositionSpec.JsonString(
      Res.readBytes("files/loading.json").decodeToString()
    )
  }

  // Apply the given color to the lottie animation
  val dynamicProperties =
    rememberLottieDynamicProperties {
      // Some callers default to a transparent color which previously had
      // no effect on the animation, here we preserve that expectation.
      if (color.alpha != 0f) {
        shapeLayer("Shape Layer 1") {
          stroke("Polystar 4", "Gradient Stroke 1") {
            colorFilter { ColorFilter.tint(color) }
          }
        }
      }
    }
  return rememberLottiePainter(
    composition = loadingAnimationComposition,
    iterations = Compottie.IterateForever,
    dynamicProperties = dynamicProperties
  )
}

@Composable
fun LoadingBadge(
  modifier: Modifier = Modifier,
  color: Color = WalletTheme.colors.foreground,
) {
  if (LocalIsPreviewTheme.current) {
    // NOTE: Display static loader image for preview/snapshot tests
    Icon(
      modifier = modifier,
      painter = painterResource(Res.drawable.loading_badge_static),
      contentDescription = null,
      tint = color
    )
  } else {
    val painter = LoadingBadgePainter()
    Image(
      painter = painter,
      modifier = modifier,
      contentScale = ContentScale.FillBounds,
      contentDescription = null
    )
  }
}

@OptIn(ExperimentalResourceApi::class, ExperimentalCompottieApi::class)
@Composable
fun LoadingBadgePainter(color: Color? = null): Painter {
  if (LocalIsPreviewTheme.current) {
    // NOTE: Display static loader image for preview/snapshot tests
    return painterResource(Res.drawable.loading_badge_static)
  }

  // Apply the given color to the lottie animation
  val dynamicProperties = rememberLottieDynamicProperties(color) {
    color?.let {
      shapeLayer("Shape Layer 2") {
        stroke {
          colorFilter { ColorFilter.tint(color) }
        }
      }
    }
  }

  val loadingAnimationComposition by rememberLottieComposition(color) {
    LottieCompositionSpec.JsonString(
      Res.readBytes("files/loader_badge.json").decodeToString()
    )
  }

  return rememberLottiePainter(
    composition = loadingAnimationComposition,
    iterations = Compottie.IterateForever,
    dynamicProperties = dynamicProperties
  )
}
