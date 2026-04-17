package build.wallet.ui.app.loading

import androidx.compose.animation.*
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment.Companion.Center
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.unit.dp
import bitkey.ui.framework_public.generated.resources.Res
import bitkey.ui.framework_public.generated.resources.bitkey_logo_mark
import bitkey.ui.framework_public.generated.resources.bitkey_word_mark
import bitkey.ui.framework_public.generated.resources.bitkey_word_mark_b
import bitkey.ui.framework_public.generated.resources.bitkey_word_mark_e
import bitkey.ui.framework_public.generated.resources.bitkey_word_mark_i
import bitkey.ui.framework_public.generated.resources.bitkey_word_mark_k
import bitkey.ui.framework_public.generated.resources.bitkey_word_mark_t
import bitkey.ui.framework_public.generated.resources.bitkey_word_mark_y
import build.wallet.statemachine.core.SplashBodyModel
import kotlinx.coroutines.delay
import org.jetbrains.compose.resources.painterResource
import kotlin.time.Duration.Companion.ZERO

internal val LogoMarkHeight = 38.dp
internal val WordMarkHeight = 34.dp
internal val WordMarkTopPadding = 6.dp

@Composable
fun SplashScreen(
  modifier: Modifier = Modifier,
  model: SplashBodyModel,
) {
  var isBitkeyWordMarkVisible by remember {
    mutableStateOf(false)
  }

  LaunchedEffect("show word mark with animation") {
    delay(model.bitkeyWordMarkAnimationDelay)
    isBitkeyWordMarkVisible = true
  }

  val animationMilliseconds = model.bitkeyWordMarkAnimationDuration.inWholeMilliseconds.toInt()

  Box(
    modifier = modifier
      .fillMaxSize()
      .background(Color.Black),
    contentAlignment = Center
  ) {
    Row(
      modifier =
        Modifier.animateContentSize(
          animationSpec = tween(durationMillis = animationMilliseconds)
        ),
      horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
      BitkeyLogoMark()

      if (model.bitkeyWordMarkAnimationDelay != ZERO) {
        BitkeyWordMarkCascadeAnimatedVisibility(model, isBitkeyWordMarkVisible)
      } else {
        BitkeyWordMark()
      }
    }
  }
}

@Composable
private fun BitkeyLogoMark() {
  Image(
    modifier = Modifier.height(LogoMarkHeight),
    painter = painterResource(Res.drawable.bitkey_logo_mark),
    contentDescription = "Bitkey Logo",
    colorFilter = ColorFilter.tint(Color.White)
  )
}

@Composable
private fun BitkeyWordMark() {
  Image(
    modifier =
      Modifier
        .padding(top = WordMarkTopPadding)
        .height(WordMarkHeight),
    painter = painterResource(Res.drawable.bitkey_word_mark),
    contentDescription = "Bitkey",
    colorFilter = ColorFilter.tint(Color.White)
  )
}

@Composable
private fun BitkeyWordMarkCascadeAnimatedVisibility(
  model: SplashBodyModel,
  isBitkeyWordMarkVisible: Boolean,
) {
  if (isBitkeyWordMarkVisible) {
    BitkeyWordMarkCascade(model)
  }
}

@Composable
private fun BitkeyWordMarkCascade(model: SplashBodyModel) {
  var areLettersVisible by remember { mutableStateOf(false) }
  val letterDrawables = listOf(
    Res.drawable.bitkey_word_mark_b,
    Res.drawable.bitkey_word_mark_i,
    Res.drawable.bitkey_word_mark_t,
    Res.drawable.bitkey_word_mark_k,
    Res.drawable.bitkey_word_mark_e,
    Res.drawable.bitkey_word_mark_y
  )

  LaunchedEffect(Unit) {
    areLettersVisible = true
  }

  val animationMilliseconds = model.bitkeyWordMarkAnimationDuration.inWholeMilliseconds.toInt()
  val letterAnimationDurationMilliseconds = (animationMilliseconds / 2).coerceAtLeast(1)
  val letterStaggerMilliseconds =
    if (letterDrawables.size > 1) {
      (
        (animationMilliseconds - letterAnimationDurationMilliseconds).coerceAtLeast(0) /
          (letterDrawables.size - 1)
      ).coerceAtLeast(1)
    } else {
      0
    }

  Row(
    modifier = Modifier.padding(top = WordMarkTopPadding),
    horizontalArrangement = Arrangement.spacedBy(0.dp)
  ) {
    letterDrawables.forEachIndexed { index, drawable ->
      val alpha by animateFloatAsState(
        targetValue = if (areLettersVisible) 1f else 0f,
        animationSpec =
          tween(
            durationMillis = letterAnimationDurationMilliseconds,
            delayMillis = letterStaggerMilliseconds * index,
            easing = LinearOutSlowInEasing
          ),
        label = "bitkey-wordmark-letter-$index"
      )

      Image(
        modifier = Modifier
          .height(WordMarkHeight)
          .alpha(alpha),
        painter = painterResource(drawable),
        contentDescription = null,
        colorFilter = ColorFilter.tint(Color.White)
      )
    }
  }
}
