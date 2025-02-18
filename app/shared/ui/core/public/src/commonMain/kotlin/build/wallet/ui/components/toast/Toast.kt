package build.wallet.ui.components.toast

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import bitkey.shared.ui_core_public.generated.resources.Res
import bitkey.shared.ui_core_public.generated.resources.inter_medium
import build.wallet.statemachine.core.Icon
import build.wallet.ui.components.icon.IconImage
import build.wallet.ui.components.icon.dp
import build.wallet.ui.components.label.Label
import build.wallet.ui.model.icon.IconModel
import build.wallet.ui.model.icon.IconSize
import build.wallet.ui.model.toast.ToastModel
import kotlinx.coroutines.delay
import org.jetbrains.compose.resources.Font
import kotlin.time.Duration.Companion.seconds

/*
 A toast is a component that has a leading icon and title text
 It displays temporarily at the bottom of the screen with an animated appearance and dismissal
 */
@Composable
fun Toast(model: ToastModel) {
  val isVisible by produceState(false, model) {
    value = true
    delay(2.5.seconds)
    value = false
  }

  AnimatedVisibility(
    visible = isVisible,
    enter = slideInVertically(
      initialOffsetY = { fullHeight -> fullHeight },
      animationSpec = tween(durationMillis = 300)
    ),
    exit = slideOutVertically(
      targetOffsetY = { fullHeight -> fullHeight },
      animationSpec = tween(durationMillis = 300)
    )
  ) {
    ToastComposable(model)
  }
}

@Composable
internal fun ToastComposable(model: ToastModel?) {
  Column(
    modifier = Modifier
      .fillMaxSize(),
    verticalArrangement = Arrangement.Bottom
  ) {
    // Curved left and right corners on the top of the toast
    Row(
      modifier = Modifier
        .offset(0.dp, 0.5.dp)
        .fillMaxWidth()
    ) {
      IconImage(
        model = IconModel(
          icon = Icon.SubtractLeft,
          iconSize = IconSize.Subtract
        ),
        color = Color.Black
      )

      Spacer(modifier = Modifier.weight(1f))

      IconImage(
        model = IconModel(
          icon = Icon.SubtractRight,
          iconSize = IconSize.Subtract
        ),
        color = Color.Black
      )
    }

    Row(
      modifier = Modifier
        .background(Color.Black)
        .padding(start = 24.dp, end = 24.dp)
        .fillMaxWidth()
    ) {
      model?.leadingIcon?.let { icon ->
        Box {
          Box(
            modifier = Modifier
              .padding(top = 20.dp, start = 2.dp)
              .background(model.iconStrokeColor.color(), shape = CircleShape)
              .size(icon.iconSize.dp - 4.dp, icon.iconSize.dp - 4.dp)
          ).takeIf { model.iconStrokeColor != ToastModel.IconStrokeColor.Unspecified }
          IconImage(
            modifier = Modifier
              .padding(top = 18.dp, bottom = 34.dp, end = 8.dp),
            model = icon
          )
        }
      }
      Label(
        modifier = Modifier.padding(top = 18.dp, bottom = 34.dp),
        text = model?.title.orEmpty(),
        style = TextStyle(
          fontSize = 16.sp,
          lineHeight = 24.sp,
          fontFamily = FontFamily(Font(Res.font.inter_medium)),
          fontWeight = FontWeight(500),
          color = Color.White
        )
      )
    }
  }
}

private fun ToastModel.IconStrokeColor.color() =
  when (this) {
    ToastModel.IconStrokeColor.Unspecified -> Color.Unspecified
    ToastModel.IconStrokeColor.White -> Color.White
    ToastModel.IconStrokeColor.Black -> Color.Black
  }
