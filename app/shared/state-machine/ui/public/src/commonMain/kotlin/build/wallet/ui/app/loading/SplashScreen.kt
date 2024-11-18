package build.wallet.ui.app.loading

import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Icon
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment.Companion.Center
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import bitkey.shared.ui_core_public.generated.resources.Res
import bitkey.shared.ui_core_public.generated.resources.bitkey_logo_mark
import bitkey.shared.ui_core_public.generated.resources.bitkey_word_mark
import build.wallet.statemachine.core.SplashBodyModel
import kotlinx.coroutines.delay
import org.jetbrains.compose.resources.painterResource
import kotlin.time.Duration.Companion.ZERO

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
      horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
      BitkeyLogoMark()

      if (model.bitkeyWordMarkAnimationDelay != ZERO) {
        BitkeyWordMarkAnimatedVisibility(model, isBitkeyWordMarkVisible)
      } else {
        BitkeyWordMark()
      }
    }
  }
}

@Composable
private fun BitkeyLogoMark() {
  Icon(
    modifier = Modifier.height(38.dp),
    painter = painterResource(Res.drawable.bitkey_logo_mark),
    contentDescription = "Bitkey Logo",
    tint = Color.White
  )
}

@Composable
private fun BitkeyWordMark() {
  Icon(
    modifier =
      Modifier
        .padding(top = 5.dp)
        .height(38.dp),
    painter = painterResource(Res.drawable.bitkey_word_mark),
    contentDescription = "Bitkey",
    tint = Color.White
  )
}

@Composable
private fun BitkeyWordMarkAnimatedVisibility(
  model: SplashBodyModel,
  isBitkeyWordMarkVisible: Boolean,
) {
  val animationMilliseconds = model.bitkeyWordMarkAnimationDuration.inWholeMilliseconds.toInt()
  AnimatedVisibility(
    visible = isBitkeyWordMarkVisible,
    enter =
      slideInHorizontally(
        initialOffsetX = { fullWidth -> fullWidth },
        animationSpec = tween(durationMillis = animationMilliseconds)
      ).plus(fadeIn(animationSpec = tween(durationMillis = animationMilliseconds))),
    exit =
      slideOutHorizontally(
        targetOffsetX = { fullWidth -> fullWidth },
        animationSpec = tween(durationMillis = animationMilliseconds)
      ).plus(fadeOut(animationSpec = tween(durationMillis = animationMilliseconds)))
  ) {
    BitkeyWordMark()
  }
}
