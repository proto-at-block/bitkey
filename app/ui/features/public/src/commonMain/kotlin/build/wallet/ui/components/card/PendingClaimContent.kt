package build.wallet.ui.components.card

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import bitkey.ui.framework_public.generated.resources.Res
import bitkey.ui.framework_public.generated.resources.inter_medium
import bitkey.ui.framework_public.generated.resources.inter_regular
import build.wallet.statemachine.core.Icon
import build.wallet.statemachine.core.LabelModel
import build.wallet.statemachine.core.TimerDirection
import build.wallet.statemachine.moneyhome.card.CardModel
import build.wallet.ui.components.callout.CalloutButton
import build.wallet.ui.components.icon.IconButton
import build.wallet.ui.components.icon.IconImage
import build.wallet.ui.components.label.Label
import build.wallet.ui.components.progress.CircularProgressIndicator
import build.wallet.ui.model.StandardClick
import build.wallet.ui.model.callout.CalloutModel
import build.wallet.ui.model.icon.IconBackgroundType
import build.wallet.ui.model.icon.IconModel
import build.wallet.ui.model.icon.IconSize
import build.wallet.ui.model.icon.IconTint
import build.wallet.ui.theme.WalletTheme
import org.jetbrains.compose.resources.Font

@Composable
fun PendingClaimContent(
  model: CardModel.PendingClaim,
  modifier: Modifier = Modifier,
) {
  val cornerRadius = 8.dp
  Box(
    modifier = modifier
      .fillMaxWidth()
      .background(
        color = WalletTheme.colors.secondary,
        shape = RoundedCornerShape(size = cornerRadius)
      ),
    contentAlignment = Alignment.CenterStart
  ) {
    Row(
      modifier = Modifier
        .fillMaxWidth()
        .padding(16.dp),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.Start
    ) {
      PendingClaimLeadingIcon(model = model)

      Column(
        modifier = Modifier
          .padding(start = 16.dp)
          .weight(1f),
        horizontalAlignment = Alignment.Start,
        verticalArrangement = Arrangement.Center
      ) {
        Label(
          text = model.title,
          style = TextStyle(
            fontSize = 16.sp,
            lineHeight = 24.sp,
            fontFamily = FontFamily(Font(Res.font.inter_medium)),
            fontWeight = FontWeight(500),
            color = WalletTheme.colors.foreground
          )
        )
        Label(
          model = LabelModel.StringModel(model.subtitle),
          modifier = Modifier.padding(top = 4.dp),
          style = TextStyle(
            fontSize = 16.sp,
            lineHeight = 24.sp,
            fontFamily = FontFamily(Font(Res.font.inter_regular)),
            fontWeight = FontWeight(400),
            color = WalletTheme.colors.foreground60
          )
        )
      }

      PendingClaimAction(model = model)
    }
  }
}

@Composable
private fun PendingClaimLeadingIcon(model: CardModel.PendingClaim) {
  Box {
    IconImage(model = pendingClaimLeadingIconModel(state = model.state))
    if (model.state == CardModel.PendingClaim.State.Pending) {
      CircularProgressIndicator(
        progress = model.progress.value,
        direction = TimerDirection.Clockwise,
        remainingSeconds = model.timeRemaining.inWholeSeconds,
        size = 40.dp,
        indicatorColor = WalletTheme.colors.foreground30,
        backgroundColor = Color.Unspecified,
        strokeWidth = 3.dp
      )
    }
  }
}

private fun pendingClaimLeadingIconModel(state: CardModel.PendingClaim.State): IconModel {
  if (state == CardModel.PendingClaim.State.Pending) {
    return IconModel(
      icon = Icon.ClockHands,
      iconSize = IconSize.Accessory,
      iconTint = IconTint.On60,
      iconBackgroundType = IconBackgroundType.Circle(
        circleSize = IconSize.Large,
        color = IconBackgroundType.Circle.CircleColor.SubtleBackground
      )
    )
  }

  return IconModel(
    icon = Icon.CheckInheritance,
    iconSize = IconSize.Accessory,
    iconTint = IconTint.On60,
    iconBackgroundType = IconBackgroundType.Circle(
      circleSize = IconSize.Large,
      color = IconBackgroundType.Circle.CircleColor.SubtleBackground
    )
  )
}

@Composable
private fun PendingClaimAction(model: CardModel.PendingClaim) {
  Column {
    if (model.state == CardModel.PendingClaim.State.Pending) {
      model.onClick?.let { onClick ->
        IconButton(
          modifier = Modifier.padding(start = 12.dp, end = 0.dp),
          iconModel = IconModel(
            icon = Icon.XCircleFill,
            iconSize = IconSize.Accessory,
            iconTint = IconTint.On60
          ),
          onClick = {
            onClick.invoke()
          }
        )
      }
    } else {
      CalloutButton(
        Icon.ArrowRight,
        WalletTheme.colors.foreground60,
        CalloutModel.Treatment.Default,
        StandardClick { model.onClick?.invoke() },
        useInverseButtonStyle = true
      )
    }
  }
}
