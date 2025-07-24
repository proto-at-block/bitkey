package build.wallet.ui.components.card

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
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
import build.wallet.ui.theme.LocalTheme
import build.wallet.ui.theme.Theme
import build.wallet.ui.theme.WalletTheme
import org.jetbrains.compose.resources.Font

@Composable
fun PendingClaimContent(
  model: CardModel.CardContent.PendingClaim,
  modifier: Modifier = Modifier,
) {
  val theme = LocalTheme.current
  Box(
    modifier = modifier
      .fillMaxWidth()
      .background(
        color = WalletTheme.colors.calloutInformationBackground,
        shape = RoundedCornerShape(size = 16.dp)
      ),
    contentAlignment = Alignment.CenterStart
  ) {
    Row(
      modifier = Modifier
        .fillMaxWidth()
        .padding(16.dp),
      verticalAlignment = if (model.isPendingClaim) Alignment.Top else Alignment.CenterVertically,
      horizontalArrangement = Arrangement.Start
    ) {
      Box {
        IconImage(
          model = IconModel(
            icon = if (model.isPendingClaim) Icon.SmallIconClockHands else Icon.SmallIconCheckInheritance,
            iconSize = IconSize.Accessory,
            iconTint = when (theme) {
              Theme.LIGHT -> IconTint.Information
              Theme.DARK -> IconTint.Foreground
            },
            iconBackgroundType = IconBackgroundType.Circle(
              circleSize = IconSize.Large,
              color = when (theme) {
                Theme.LIGHT -> IconBackgroundType.Circle.CircleColor.Information
                Theme.DARK -> IconBackgroundType.Circle.CircleColor.TransparentForeground
              }
            )
          )
        )
        CircularProgressIndicator(
          progress = model.progress.value,
          direction = TimerDirection.Clockwise,
          remainingSeconds = model.timeRemaining.inWholeSeconds,
          size = 40.dp,
          indicatorColor = when (theme) {
            Theme.LIGHT -> WalletTheme.colors.calloutInformationTrailingIconBackground.copy(alpha = 0.33f)
            Theme.DARK -> WalletTheme.colors.foreground
          },
          backgroundColor = Color.Unspecified,
          strokeWidth = 4.dp
        )
      }

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
            color = WalletTheme.colors.calloutInformationTitle
          )
        )
        Label(
          model = LabelModel.StringModel(model.subtitle),
          modifier = Modifier
            .padding(top = 4.dp)
            .alpha(0.6f),
          style = TextStyle(
            fontSize = 16.sp,
            lineHeight = 24.sp,
            fontFamily = FontFamily(Font(Res.font.inter_regular)),
            fontWeight = FontWeight(400),
            color = WalletTheme.colors.calloutInformationSubtitle
          )
        )
      }

      Column {
        if (model.isPendingClaim) {
          model.onClick?.let {
            IconButton(
              modifier = Modifier
                .padding(start = 12.dp, end = 0.dp),
              iconModel = IconModel(
                icon = Icon.SmallIconXFilled,
                iconSize = IconSize.Accessory,
                iconTint = when (theme) {
                  Theme.LIGHT -> IconTint.Information
                  Theme.DARK -> IconTint.On30
                }
              ),
              onClick = {
                it.invoke()
              }
            )
          }
        } else {
          CalloutButton(
            Icon.SmallIconArrowRight,
            WalletTheme.colors.calloutDefaultTrailingIcon,
            CalloutModel.Treatment.Information,
            StandardClick { model.onClick?.invoke() }
          )
        }
      }
    }
  }
}
