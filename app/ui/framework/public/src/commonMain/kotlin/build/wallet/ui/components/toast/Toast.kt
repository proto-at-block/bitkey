package build.wallet.ui.components.toast

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import bitkey.ui.framework_public.generated.resources.Res
import bitkey.ui.framework_public.generated.resources.inter_medium
import build.wallet.ui.components.icon.IconImage
import build.wallet.ui.components.icon.dp
import build.wallet.ui.components.label.Label
import build.wallet.ui.model.toast.ToastModel
import org.jetbrains.compose.resources.Font

/*
 A toast is a component that has a leading icon and title text
 It displays temporarily at the bottom of the screen with an animated appearance and dismissal
 */
@Composable
fun Toast(model: ToastModel?) {
  val currentModel: ToastModel? by produceState(model, model) {
    value = model ?: value
  }
  val isVisible by produceState(model != null, model) {
    value = model != null
  }

  Box(
    modifier = Modifier
      .animateContentSize()
      .height(if (isVisible) Dp.Unspecified else 0.dp),
    contentAlignment = Alignment.BottomCenter
  ) {
    ToastComposable(
      model = currentModel
    )
  }
}

@Composable
internal fun ToastComposable(model: ToastModel?) {
  Column(
    modifier = Modifier
      .wrapContentHeight(Alignment.Top, unbounded = true)
      .fillMaxWidth()
      .drawWithCache {
        // Curved left and right corners on the top of the toast
        val cornerSize = 18.dp.toPx()

        fun createPath(left: Boolean): Path {
          return Path().apply {
            moveTo(0f, 0f)
            cubicTo(
              x1 = 0f,
              y1 = 0f,
              x2 = 0f,
              y2 = cornerSize,
              x3 = if (left) cornerSize else -cornerSize,
              y3 = cornerSize
            )
            lineTo(0f, cornerSize)
            close()
          }
        }

        val leftPath = createPath(left = true)
        val rightPath = createPath(left = false)
        onDrawBehind {
          drawPath(path = leftPath, color = Color.Black)
          translate(left = size.width) {
            drawPath(path = rightPath, color = Color.Black)
          }
        }
      },
    verticalArrangement = Arrangement.Bottom
  ) {
    Spacer(Modifier.height(18.dp))
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
