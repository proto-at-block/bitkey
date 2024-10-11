package build.wallet.ui.components.header

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import build.wallet.statemachine.core.form.FormHeaderModel
import build.wallet.ui.components.icon.IconImage
import build.wallet.ui.theme.WalletTheme

@Composable
fun CustomHeaderContent(model: FormHeaderModel.CustomContent) {
  when (model) {
    is FormHeaderModel.CustomContent.PartnershipTransferAnimation -> PartnershipTransferAnimation(model)
  }
}

@Composable
private fun PartnershipTransferAnimation(
  model: FormHeaderModel.CustomContent.PartnershipTransferAnimation,
) {
  Row(
    modifier = Modifier.fillMaxWidth()
      .padding(top = 24.dp),
    horizontalArrangement = Arrangement.Start,
    verticalAlignment = Alignment.CenterVertically
  ) {
    IconImage(model = model.bitkeyIcon)
    Spacer(modifier = Modifier.size(6.dp))
    DotsLoadingIndicator()
    Spacer(modifier = Modifier.size(6.dp))
    IconImage(model = model.partnerIcon)
  }
}

@Composable
fun DotsLoadingIndicator() {
  // Infinite transition for the animation
  val infiniteTransition = rememberInfiniteTransition()

  // Animating the opacity for each dot
  val dot1Alpha by infiniteTransition.animateFloat(
    initialValue = 0.1f,
    targetValue = 1f,
    animationSpec = infiniteRepeatable(
      animation = tween(500, easing = LinearEasing),
      repeatMode = RepeatMode.Reverse,
      initialStartOffset = StartOffset(0)
    )
  )

  val dot2Alpha by infiniteTransition.animateFloat(
    initialValue = 0.1f,
    targetValue = 1f,
    animationSpec = infiniteRepeatable(
      animation = tween(500, easing = LinearEasing),
      repeatMode = RepeatMode.Reverse,
      initialStartOffset = StartOffset(200)
    )
  )

  val dot3Alpha by infiniteTransition.animateFloat(
    initialValue = 0.1f,
    targetValue = 1f,
    animationSpec = infiniteRepeatable(
      animation = tween(500, easing = LinearEasing),
      repeatMode = RepeatMode.Reverse,
      initialStartOffset = StartOffset(400)
    )
  )

  // Creating the row with 3 dots
  Row(
    modifier = Modifier
      .wrapContentSize(),
    horizontalArrangement = Arrangement.spacedBy(8.dp),
    verticalAlignment = Alignment.CenterVertically
  ) {
    Dot(alpha = dot1Alpha)
    Dot(alpha = dot2Alpha)
    Dot(alpha = dot3Alpha)
  }
}

// Single Dot composable that takes in the current alpha
@Composable
fun Dot(alpha: Float) {
  Box(
    modifier = Modifier
      .size(8.dp)
      .background(WalletTheme.colors.bitkeyPrimary.copy(alpha = alpha), shape = RoundedCornerShape(3.dp))
  )
}
